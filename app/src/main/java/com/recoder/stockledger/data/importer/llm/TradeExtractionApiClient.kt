package com.recoder.stockledger.data.importer.llm

import android.util.Base64
import android.util.Log
import com.recoder.stockledger.data.Market
import com.recoder.stockledger.data.TradeType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.delay
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.format.DateTimeParseException

interface TradeExtractionApiClient {
    suspend fun extractTradesFromText(
        text: String,
        passwordHint: String? = null,
    ): LlmExtractionResult
}

class OpenAiTradeExtractionClient(
    private val apiKey: String,
    private val model: String = "gpt-4o",
    private val baseUrl: String = "https://api.openai.com/v1/chat/completions",
) : TradeExtractionApiClient {

    private companion object {
        const val TAG = "OpenAiTradeExtractionClient"
    }

    private val normalizedBaseUrl: String = if (baseUrl.endsWith("/chat/completions")) {
        baseUrl
    } else {
        baseUrl.removeSuffix("/") + "/chat/completions"
    }


    override suspend fun extractTradesFromText(
        text: String,
        passwordHint: String?,
    ): LlmExtractionResult = withContext(Dispatchers.IO) {
        val requestBody = buildTextRequestBody(text, passwordHint)
        Log.d(TAG, "Text request body size: ${requestBody.length} chars")
        executeWithRetry { sendRequest(requestBody) }
    }

    private fun sendRequest(requestBody: String): LlmExtractionResult {
        val connection = URL(normalizedBaseUrl).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.connectTimeout = 60_000
            connection.readTimeout = 300_000 // Increased to 5 minutes for long LLM inference

            connection.outputStream.use { os ->
                os.write(requestBody.toByteArray(Charsets.UTF_8))
            }

            val responseCode = connection.responseCode
            val responseText = if (responseCode in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $responseCode"
            }

            Log.d(TAG, "Response code: $responseCode")

            if (responseCode !in 200..299) {
                return LlmExtractionResult.Error("API error $responseCode: $responseText")
            }

            return parseOpenAiResponse(responseText)
        } catch (e: Exception) {
            Log.e(TAG, "API call failed", e)
            return LlmExtractionResult.Error("Network/IO error: ${e.message}")
        }
        finally {

        }
    }

    /**
     * Retry network/timeout errors with exponential backoff.
     * Does NOT retry client errors (4xx) or successful parse errors.
     */
    private suspend fun executeWithRetry(
        maxRetries: Int = 2,
        initialDelayMs: Long = 2_000L,
        request: () -> LlmExtractionResult,
    ): LlmExtractionResult {
        var lastResult: LlmExtractionResult? = null
        for (attempt in 0..maxRetries) {
            val result = request()
            if (result is LlmExtractionResult.Success) {
                return result
            }
            lastResult = result
            // Only retry on network/timeout errors, not on 4xx client errors
            if (result is LlmExtractionResult.Error) {
                val isRetryable = result.message.contains("Network/IO error", ignoreCase = true)
                    || result.message.contains("timeout", ignoreCase = true)
                    || result.message.contains("API error 5", ignoreCase = true) // 5xx server errors
                    || result.message.contains("API error 429", ignoreCase = true) // rate limit
                if (!isRetryable) {
                    Log.w(TAG, "Non-retryable error, giving up: ${result.message}")
                    return result
                }
            }
            if (attempt < maxRetries) {
                val delayMs = initialDelayMs * (1L shl attempt)
                Log.w(TAG, "Attempt ${attempt + 1} failed, retrying in ${delayMs}ms...")
                delay(delayMs)
            }
        }
        return lastResult ?: LlmExtractionResult.Error("Unknown error after retries")
    }


    private fun buildTextRequestBody(text: String, passwordHint: String?): String {
        val messages = JSONArray()
        messages.put(JSONObject().apply {
            put("role", "system")
            put("content", TradeExtractionPrompt.systemPrompt())
        })
        messages.put(JSONObject().apply {
            put("role", "user")
            put("content", TradeExtractionPrompt.textUserPrompt(text, passwordHint))
        })

        val root = JSONObject().apply {
            put("model", model)
            put("messages", messages)
            // JSON Output
            put("response_format", JSONObject().apply {
                put("type", "json_object")
            })
            // 关闭 thinking，避免 reasoning_content 占用大量 token
            put("thinking", JSONObject().apply {
                put("type", "disabled")
            })
            put("max_tokens", 4096)
            put("temperature", 0.0)
        }
        return root.toString()
    }

    private fun parseOpenAiResponse(responseText: String): LlmExtractionResult {
        return try {
            val json = JSONObject(responseText)
            val choices = json.optJSONArray("choices")
            if (choices == null || choices.length() == 0) {
                return LlmExtractionResult.Error("No choices in API response")
            }
            val choiceObj = choices.getJSONObject(0)

            val finishReason = choiceObj.optString("finish_reason", "")
            when (finishReason) {
                "length" -> {
                    return LlmExtractionResult.Error(
                        "模型输出被截断，无法解析交易记录。请增大 max_tokens、缩短输入文本，或分段解析。"
                    )
                }
                "content_filter" -> {
                    return LlmExtractionResult.Error(
                        "模型输出被内容过滤器截断，无法解析交易记录。"
                    )
                }
                "insufficient_system_resource" -> {
                    return LlmExtractionResult.Error(
                        "模型推理资源不足导致输出中断，请稍后重试。"
                    )
                }
                "tool_calls" -> {
                    return LlmExtractionResult.Error(
                        "模型返回了 tool_calls，而不是交易 JSON。"
                    )
                }
            }

            val messageObj = choiceObj.optJSONObject("message")
            val content = messageObj?.optString("content", "") ?: ""
            if (content.isBlank()) {
                return LlmExtractionResult.Error("Empty content in API response")
            }
            parseTradeJson(content)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse response", e)
            LlmExtractionResult.Error("Parse error: ${e.message}")
        }
    }

    private fun parseTradeJson(content: String): LlmExtractionResult {
        return try {
            val root = JSONObject(content)
            val tradesArray = root.optJSONArray("trades") ?: JSONArray()
            val trades = mutableListOf<LlmExtractedTrade>()
            for (i in 0 until tradesArray.length()) {
                val tradeObj = tradesArray.getJSONObject(i)
                val trade = parseSingleTrade(tradeObj)
                if (trade != null) {
                    trades.add(trade)
                }
            }
            LlmExtractionResult.Success(trades)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse trade JSON", e)
            LlmExtractionResult.Error("Trade JSON parse error: ${e.message}")
        }
    }

    private fun parseSingleTrade(obj: JSONObject): LlmExtractedTrade? {
        val tradeDateStr = obj.optString("tradeDate", obj.optString("trade_date", ""))
        val tradeDate = try {
            LocalDate.parse(tradeDateStr)
        } catch (_: DateTimeParseException) {
            null
        } ?: return null

        val tradeType = when (obj.optString("tradeType", obj.optString("trade_type", "")).uppercase()) {
            "BUY" -> TradeType.BUY
            "SELL" -> TradeType.SELL
            else -> return null
        }

        val symbol = obj.optString("symbol", "").trim()
        if (symbol.isBlank()) return null

        val market = when (obj.optString("market", "").uppercase()) {
            "US" -> Market.US
            "HK", "HONG_KONG" -> Market.HK
            "CN", "CHINA", "A_SHARE" -> Market.A_SHARE
            else -> Market.US
        }

        return LlmExtractedTrade(
            tradeType = tradeType,
            market = market,
            symbol = symbol,
            name = obj.optString("name", symbol).trim(),
            tradeDate = tradeDate,
            tradeTime = obj.optString("tradeTime", "00:00"),
            price = obj.optDouble("price", 0.0).coerceAtLeast(0.0),
            quantity = obj.optDouble("quantity", 0.0).coerceAtLeast(0.0),
            commission = obj.optDouble("commission", 0.0).coerceAtLeast(0.0),
            tax = obj.optDouble("tax", 0.0).coerceAtLeast(0.0),
            note = obj.optString("note", ""),
            createdAt = obj.optLong("createdAt", 0L),
        )
    }
}

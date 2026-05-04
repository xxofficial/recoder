package com.recoder.stockledger.data.importer.vision

import android.util.Base64
import android.util.Log
import com.recoder.stockledger.data.Market
import com.recoder.stockledger.data.TradeType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.format.DateTimeParseException

interface VisionApiClient {
    suspend fun extractTrades(
        images: List<ByteArray>,
        passwordHint: String? = null,
    ): VisionExtractionResult

    suspend fun extractTradesFromText(
        text: String,
        passwordHint: String? = null,
    ): VisionExtractionResult
}

class OpenAiVisionClient(
    private val apiKey: String,
    private val model: String = "gpt-4o",
    private val baseUrl: String = "https://api.openai.com/v1/chat/completions",
) : VisionApiClient {

    private companion object {
        const val TAG = "OpenAiVisionClient"
    }

    override suspend fun extractTrades(
        images: List<ByteArray>,
        passwordHint: String?,
    ): VisionExtractionResult = withContext(Dispatchers.IO) {
        try {
            val requestBody = buildRequestBody(images, passwordHint)
            Log.d(TAG, "Request body size: ${requestBody.length} chars")

            val connection = URL(baseUrl).openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.connectTimeout = 60_000
            connection.readTimeout = 120_000

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
                return@withContext VisionExtractionResult.Error("API error $responseCode: $responseText")
            }

            parseOpenAiResponse(responseText)
        } catch (e: Exception) {
            Log.e(TAG, "API call failed", e)
            VisionExtractionResult.Error("Network/IO error: ${e.message}")
        }
    }

    override suspend fun extractTradesFromText(
        text: String,
        passwordHint: String?,
    ): VisionExtractionResult = withContext(Dispatchers.IO) {
        try {
            val requestBody = buildTextRequestBody(text, passwordHint)
            Log.d(TAG, "Text request body size: ${requestBody.length} chars")

            val connection = URL(baseUrl).openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.connectTimeout = 60_000
            connection.readTimeout = 120_000

            connection.outputStream.use { os ->
                os.write(requestBody.toByteArray(Charsets.UTF_8))
            }

            val responseCode = connection.responseCode
            val responseText = if (responseCode in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $responseCode"
            }

            Log.d(TAG, "Text response code: $responseCode")

            if (responseCode !in 200..299) {
                return@withContext VisionExtractionResult.Error("API error $responseCode: $responseText")
            }

            parseOpenAiResponse(responseText)
        } catch (e: Exception) {
            Log.e(TAG, "Text API call failed", e)
            VisionExtractionResult.Error("Network/IO error: ${e.message}")
        }
    }

    private fun buildRequestBody(images: List<ByteArray>, passwordHint: String?): String {
        val contentArray = JSONArray()
        contentArray.put(JSONObject().apply {
            put("type", "text")
            put("text", TradeExtractionPrompt.systemPrompt())
        })
        contentArray.put(JSONObject().apply {
            put("type", "text")
            put("text", TradeExtractionPrompt.userPrompt(passwordHint))
        })
        for (imgBytes in images) {
            val base64 = Base64.encodeToString(imgBytes, Base64.NO_WRAP)
            contentArray.put(JSONObject().apply {
                put("type", "image_url")
                put("image_url", JSONObject().apply {
                    put("url", "data:image/jpeg;base64,$base64")
                })
            })
        }

        val messages = JSONArray().put(JSONObject().apply {
            put("role", "user")
            put("content", contentArray)
        })

        val root = JSONObject().apply {
            put("model", model)
            put("messages", messages)
            put("response_format", JSONObject().apply { put("type", "json_object") })
            put("max_tokens", 4096)
            put("temperature", 0.0)
        }
        return root.toString()
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
            put("response_format", JSONObject().apply { put("type", "json_object") })
            put("max_tokens", 4096)
            put("temperature", 0.0)
        }
        return root.toString()
    }

    private fun parseOpenAiResponse(responseText: String): VisionExtractionResult {
        return try {
            val json = JSONObject(responseText)
            val choices = json.optJSONArray("choices")
            if (choices == null || choices.length() == 0) {
                return VisionExtractionResult.Error("No choices in API response")
            }
            val messageObj = choices.getJSONObject(0).optJSONObject("message")
            val content = messageObj?.optString("content", "") ?: ""
            parseTradeJson(content)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse response", e)
            VisionExtractionResult.Error("Parse error: ${e.message}")
        }
    }

    private fun parseTradeJson(content: String): VisionExtractionResult {
        return try {
            val root = JSONObject(content)
            val tradesArray = root.optJSONArray("trades") ?: JSONArray()
            val trades = mutableListOf<VisionExtractedTrade>()
            for (i in 0 until tradesArray.length()) {
                val tradeObj = tradesArray.getJSONObject(i)
                val trade = parseSingleTrade(tradeObj)
                if (trade != null) {
                    trades.add(trade)
                }
            }
            VisionExtractionResult.Success(trades)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse trade JSON", e)
            VisionExtractionResult.Error("Trade JSON parse error: ${e.message}")
        }
    }

    private fun parseSingleTrade(obj: JSONObject): VisionExtractedTrade? {
        val tradeDateStr = obj.optString("trade_date", "")
        val tradeDate = try {
            LocalDate.parse(tradeDateStr)
        } catch (_: DateTimeParseException) {
            null
        } ?: return null

        val tradeType = when (obj.optString("trade_type", "").uppercase()) {
            "BUY" -> TradeType.BUY
            "SELL" -> TradeType.SELL
            else -> return null
        }

        val symbol = obj.optString("symbol", "").trim()
        if (symbol.isBlank()) return null

        val market = when (obj.optString("market", "").uppercase()) {
            "US" -> Market.US
            "HK", "HONG_KONG" -> Market.HONG_KONG
            "CN", "CHINA", "A_SHARE" -> Market.A_SHARE
            else -> Market.US
        }

        val feesObj = obj.optJSONObject("fees")
        val fees = if (feesObj != null) {
            VisionExtractedFees(
                commission = feesObj.optDouble("commission", 0.0),
                platformFee = feesObj.optDouble("platform_fee", 0.0),
                settlementFee = feesObj.optDouble("settlement_fee", 0.0),
                secFee = feesObj.optDouble("sec_fee", 0.0),
                transactionFee = feesObj.optDouble("transaction_fee", 0.0),
                stampDuty = feesObj.optDouble("stamp_duty", 0.0),
                otherFees = feesObj.optDouble("other_fees", 0.0),
            )
        } else null

        return VisionExtractedTrade(
            tradeDate = tradeDate,
            tradeType = tradeType,
            symbol = symbol,
            name = obj.optString("name", symbol).trim(),
            market = market,
            exchange = obj.optString("exchange", "").trim(),
            quantity = obj.optInt("quantity", 0).coerceAtLeast(0),
            price = obj.optDouble("price", 0.0).coerceAtLeast(0.0),
            amount = obj.optDouble("amount", 0.0).coerceAtLeast(0.0),
            currencyCode = obj.optString("currency", "USD").uppercase(),
            fees = fees,
            rawText = obj.toString(),
        )
    }
}

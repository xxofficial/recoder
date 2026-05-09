package com.recoder.stockledger.data.importer.llm

object TradeExtractionPrompt {

    fun systemPrompt(): String = """
You are a specialized financial document parser. Your task is to extract stock transaction records from the provided daily trading statement text.

Rules:
1. The text contains ONLY relevant "Transaction Details" sections.
2. The statement may be in Chinese (Simplified/Traditional), English, or mixed.
3. Ticker Normalization: PDF text extraction may cause symbols to appear duplicated (e.g., "LITELITE", "AAPLAAPL"). You MUST normalize these to the correct unique symbol (e.g., "LITE", "AAPL").
4. Layout Resilience: Text extraction may reorder table headers or split a single data row into multiple interleaved lines. Use semantic context (date formats, quantity vs price magnitude) to correctly associate values.
5. Return results as a single JSON object with a "trades" array.
6. If no stock trades exist, return {"trades": []}.
7. Do not guess or hallucinate. Only extract data clearly present in the source.
""".trimIndent()

    fun textUserPrompt(rawText: String, passwordHint: String? = null): String = buildString {
        append("The following text was extracted from a daily stock trading statement PDF.\n\n")
        if (passwordHint != null) {
            append("Note: The PDF was password-protected with password: $passwordHint\n\n")
        }
        append(fieldInstructions())
        append("\nReturn EXACTLY this JSON structure (no markdown, no extra text):\n")
        append(jsonExample())
        append("\n--- BEGIN EXTRACTED PDF TEXT ---\n")
        append(rawText)
        append("\n--- END EXTRACTED PDF TEXT ---\n")
    }

    private fun fieldInstructions(): String = buildString {
        append("TRADE MERGING RULES:\n")
        append("- Under the same BUY/SELL direction, if there are multiple individual trades with the SAME symbol, market, currency, and settlement date, AND they share the same \"合计Total\" and same fee details below them, you MUST MERGE them into a SINGLE record.\n")
        append("- For merged records:\n")
        append("  - quantity = sum of all individual trade quantities.\n")
        append("  - price = the combined average price or weighted average price.\n")
        append("  - tradeTime = the earliest trade time (HH:mm) among them.\n")
        append("  - createdAt = the UTC+8 epoch milliseconds corresponding to the earliest tradeDate + tradeTime.\n")
        append("  - note = the ORIGINAL UNPROCESSED raw text of this entire trade group. Do NOT summarize it into structured text. Do a minimal cleanup (remove headers/footers, compress blank lines).\n")
        append("- Do NOT merge trades if they have different symbols, directions, markets, or fall under different fee blocks.\n\n")
        
        append("For each trade or merged trade, extract the following EXACT fields:\n")
        append("- tradeType: \"BUY\" or \"SELL\". Chinese directions: 买入/买/買入/買/B = BUY, 沽出/卖出/賣出/沽/賣/S = SELL\n")
        append("- market: \"US\" or \"HK\". (e.g. US/NASDAQ, US/NYSE -> US; HK/SEHK -> HK)\n")
        append("- symbol: Stock ticker/code (e.g., \"AAPL\", \"GLWG\", \"02050\").\n")
        append("- name: Stock name.\n")
        append("- tradeDate: YYYY-MM-DD format.\n")
        append("- tradeTime: HH:mm format.\n")
        append("- price: Merged average price per share (decimal).\n")
        append("- quantity: Merged total number of shares (integer).\n")
        append("- commission: Brokerage Commission + Platform Fee. (2 decimal places)\n")
        append("- tax: Sum of all OTHER transaction fees (Settlement Fee, SEC Fee, Finra, Central Clearing, Stamp Duty, Trading Fee, etc.) EXCLUDING accrued interest. (2 decimal places)\n")
        append("  * Note: If fees do not sum up to the subtotal, do not guess to fix it. Just report the components.\n")
        append("- note: the unprocessed original text.\n")
        append("- createdAt: Epoch milliseconds (UTC+8).\n")
    }

    private fun jsonExample(): String = """
{
  "trades": [
    {
      "tradeType": "SELL",
      "market": "US",
      "symbol": "GLWG",
      "name": "Leverage Shares每日2倍做多GLW主动型ETF",
      "tradeDate": "2026-04-24",
      "tradeTime": "03:05",
      "price": 23.91,
      "quantity": 15,
      "commission": 1.98,
      "tax": 0.42,
      "note": "...",
      "createdAt": 1776971124000
    }
  ]
}
""".trimIndent()
}

package com.recoder.stockledger.data.importer.vision

object TradeExtractionPrompt {

    fun systemPrompt(): String = """
You are a specialized financial document parser. Your task is to extract stock transaction records from daily trading statement images.

Rules:
1. ONLY extract transactions from the "Transaction Details" / "成交信息" / "Securities Transaction" section.
2. IGNORE fund transactions (基金), unsettled securities (未交收), cash deposits/withdrawals, and portfolio holdings.
3. The statement may be in Chinese (Simplified/Traditional) or English, or mixed.
4. Return results as a single JSON object with a "trades" array.
5. If no stock trades exist, return {"trades": []}.
6. Do not guess or hallucinate. Only extract data clearly visible in the images.
""".trimIndent()

    fun userPrompt(passwordHint: String? = null): String = buildString {
        append("Please analyze the attached daily stock trading statement image(s) and extract all stock transactions.\n\n")
        if (passwordHint != null) {
            append("Note: The PDF was password-protected with password: $passwordHint\n\n")
        }
        append("For each trade, extract the following fields:\n")
        append("- trade_date: YYYY-MM-DD format\n")
        append("- trade_type: \"BUY\" or \"SELL\". Chinese directions: 买入/买/買入/買 = BUY, 沽出/卖出/賣出/沽/賣 = SELL\n")
        append("- symbol: Stock ticker/code (e.g., \"AAPL\", \"0700.HK\", \"02050\"). For HK stocks, keep the 5-digit code.\n")
        append("- name: Stock name as shown in the statement\n")
        append("- market: \"US\", \"HK\", or \"CN\"\n")
        append("- exchange: \"NASDAQ\", \"NYSE\", \"AMEX\", \"BATS\", \"SEHK\", etc.\n")
        append("- quantity: Number of shares (integer)\n")
        append("- price: Price per share (decimal)\n")
        append("- amount: Total settlement amount, absolute value (decimal). For buys this is negative in the statement but use absolute value.\n")
        append("- currency: \"USD\", \"HKD\", or \"CNY\"\n")
        append("- fees (optional): Break down if available:\n")
        append("  - commission: brokerage commission\n")
        append("  - platform_fee: platform usage fee\n")
        append("  - settlement_fee: settlement/clearing fee\n")
        append("  - sec_fee: SEC regulation fee\n")
        append("  - transaction_fee: transaction activity fee\n")
        append("  - stamp_duty: stamp tax\n")
        append("  - other_fees: any other fees\n")
        append("\nReturn EXACTLY this JSON structure (no markdown, no extra text):\n")
        append("""
{
  "trades": [
    {
      "trade_date": "2026-04-22",
      "trade_type": "BUY",
      "symbol": "AAPL",
      "name": "Apple Inc",
      "market": "US",
      "exchange": "NASDAQ",
      "quantity": 100,
      "price": 175.50,
      "amount": 17550.00,
      "currency": "USD",
      "fees": {
        "commission": 1.00,
        "platform_fee": 0.99,
        "settlement_fee": 0.40,
        "sec_fee": 0.01,
        "transaction_fee": 0.00,
        "stamp_duty": 0.00,
        "other_fees": 0.00
      }
    }
  ]
}
""".trimIndent())
    }
}

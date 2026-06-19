# Profit Analysis Contract

## Security attribution

`ProfitAnalysisUiModel.securityAnalyses` is keyed by underlying security:

- Stock trades are attributed to their own `symbol`.
- Option trades are attributed to `underlyingSymbol` when available. If it is missing, the parser falls back to the leading symbol in the option code, for example `AAPL 260501C00180000` -> `AAPL`.

## Per-security fields

Each `SecurityProfitAnalysisUiModel` exposes:

- `symbol`: underlying security symbol.
- `name`: underlying security display name.
- `market`: market of the underlying security.
- `totalProfitCny`: total profit for this underlying.
- `stockProfitCny`: stock-side profit for this underlying.
- `derivativeProfitCny`: option or derivative-side profit for this underlying.
- `returnRatePercent`: reserved for per-security return rate. It remains `0.0` until a stable per-security capital base is available.
- `dailyPoints`: time series for total profit of the underlying, including attributed derivative profit.

Option unrealized profit uses the standard US option multiplier of `100`. Historical profit analysis attempts to fetch US option daily closes from Yahoo Finance so an open option can contribute mark-to-market profit before expiration.

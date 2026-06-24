# 盈亏计算重构 TODO

## 背景

当前项目中已经存在领域层 `PortfolioCalculator`，它沉淀了组合计算的核心语义，并承载了较多单元测试。但 App UI 仍在 `LedgerViewModel` 和部分页面中保留了多套独立计算逻辑：

- `LedgerViewModel.computePortfolio()`
- 收益分析历史回放
- 收益分析无历史行情 fallback
- 合伙账本份额计算中的证券数量回放
- `StockDetailScreen` 区间收益中的正股数量回放

这些重复实现容易导致规则分叉。SNXX 跨平台重复拆股问题就是典型例子：`PortfolioCalculator` 已经实现去重，但 UI 汇总路径没有复用它，导致汇总仍重复折算。

## 当前临时处理

目前已在多处手动补上同一套拆股去重逻辑：

- 同一拆股事件按 `market + symbol + tradeDate + ratio` 识别。
- 汇总计算中同一事件只应用一次。
- 各平台流水仍保留各自拆股记录，作为审计记录。

这能修正当前问题，但不是理想长期结构。

## 目标状态

让 `PortfolioCalculator` 或新的领域层服务成为组合盈亏计算的唯一事实来源：

- UI 层不再重复实现买卖、期权、分红、税费、换汇、拆股等计算规则。
- `LedgerViewModel` 只负责：
  - 将 `TransactionEntity` 转换为领域层输入。
  - 调用领域层计算器。
  - 将领域层结果映射为 UI model。
- 收益分析、收益日历、持仓详情、合伙账本份额尽量复用同一套交易回放/公司行动处理规则。

## 建议任务

- 梳理所有直接回放交易流水的位置，列出输入、输出和差异化需求。
- 为 `TransactionEntity -> PortfolioTrade` 增加统一 mapper，避免多处手写字段转换。
- 让 `LedgerViewModel.computePortfolio()` 复用 `PortfolioCalculator`，保留 UI 专用字段映射。
- 抽出可复用的公司行动处理逻辑，至少覆盖拆股/合股事件去重。
- 评估收益分析是否应拆为领域层计算器，例如 `ProfitAnalysisCalculator`。
- 评估合伙账本份额计算是否能复用领域层持仓回放结果；若不能，至少复用同一套证券数量/公司行动 helper。
- 为以下场景补回归测试：
  - 单平台拆股。
  - 跨平台重复拆股。
  - 拆股后买卖。
  - 期权不受正股拆股误影响。
  - 分红扣税、其他收入、换汇 no-op。
  - UI 汇总与 `PortfolioCalculator` 对同一输入给出一致的核心数值。

## 非目标

- 不要求本任务迁移数据库结构。
- 不要求把拆股记录改为全局唯一事件；该方向另见 `docs/corporate-action-todo.md`。
- 不要求一次性重写所有 UI，只要逐步消除重复计算入口即可。


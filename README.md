# StockLedger 📈

一款简洁优雅的股票记账 Android 应用，帮助你轻松记录和管理股票交易。

## 功能特性

- 📊 **持仓管理** — 实时查看当前持仓及盈亏状况
- 📝 **交易记录** — 快速录入买入 / 卖出交易
- 💹 **盈亏分析** — 按日期维度查看利润与亏损趋势图表
- 💱 **汇率支持** — 内置汇率数据，支持多币种换算
- 🗄️ **本地存储** — 基于 Room 数据库，数据安全持久化

## 技术栈

| 类别 | 技术 |
|------|------|
| 语言 | Kotlin |
| UI 框架 | Jetpack Compose + Material 3 |
| 架构 | MVVM (ViewModel + Repository) |
| 本地数据库 | Room |
| 导航 | Navigation Compose |
| 最低 SDK | 26 (Android 8.0) |
| 目标 SDK | 35 |

## 项目结构

```
app/src/main/java/com/recoder/stockledger/
├── MainActivity.kt                  # 入口 Activity
├── StockLedgerApplication.kt        # Application 类
├── AppContainer.kt                  # 依赖注入容器
├── StockLedgerPreferences.kt        # 偏好设置管理
├── data/
│   ├── Models.kt                    # 通用数据模型
│   ├── ProfitAnalysisModels.kt      # 盈亏分析模型
│   ├── ExchangeRates.kt             # 汇率定义
│   ├── SampleData.kt                # 示例/测试数据
│   ├── TradeFeeEstimator.kt         # 交易费用估算器
│   ├── ZhuoruiEmailSyncModels.kt    # 邮件同步模型
│   ├── local/
│   │   ├── StockLedgerDatabase.kt   # Room 本地数据库
│   │   ├── LedgerDao.kt             # 数据库 DAO
│   │   ├── TransactionEntity.kt     # 交易记录实体
│   │   └── QuoteSnapshotEntity.kt   # 行情价格快照实体
│   ├── repository/
│   │   ├── LedgerRepository.kt      # 核心数据仓库
│   │   └── ExchangeRateDataSource.kt # 汇率数据源
│   ├── importer/
│   │   ├── TradeImporter.kt         # 交易导入接口
│   │   ├── HsbcNotificationParser.kt # 汇丰交易通知解析
│   │   ├── HsbcStatementPdfParser.kt # 汇丰结单 PDF 解析
│   │   ├── USmartStatementPdfParser.kt # 盈立结单 PDF 解析
│   │   ├── ZhuoruiStatementPdfParser.kt # 华盛结单 PDF 解析
│   │   └── AndroidOcrEngine.kt      # OCR 文本识别引擎
│   └── settings/
│       └── StockLedgerSettingsStore.kt # 应用设置中心
├── domain/
│   ├── market/
│   │   └── MarketTradingSessions.kt # 交易时段判定逻辑
│   └── portfolio/
│       └── PortfolioCalculator.kt   # 组合资产/盈亏计算引擎
├── importer/
│   └── ZhuoruiEmailSyncWorker.kt    # 华盛账单邮件同步 WorkManager
├── platform/
│   └── BrokerPlatformRegistry.kt    # 券商平台元数据注册中心
└── ui/
    ├── StockLedgerApp.kt            # 路由与导航主入口 Composable
    ├── Screens.kt                   # 交易编辑与表单页面
    ├── ProfitAnalysisScreen.kt      # 盈亏分析趋势页面
    ├── Components.kt                # 通用 UI 组件
    ├── LedgerViewModel.kt           # 核心业务 UI ViewModel
    └── theme/
        ├── Color.kt                 # 颜色调色板
        ├── Theme.kt                 # Material 3 主题配置
        └── Type.kt                  # 字体排版样式
```

## 快速开始

### 环境要求

- Android Studio Hedgehog (2023.1.1) 或更高版本
- JDK 17
- Android SDK 35

### 构建 & 运行

```bash
# 克隆仓库
git clone <repository-url>
cd recoder

# 使用 Gradle 构建
./gradlew assembleDebug

# 安装到设备
./gradlew installDebug
```

或直接在 Android Studio 中打开项目，点击 **Run ▶** 即可。

## 许可证

本项目仅供个人学习和使用。

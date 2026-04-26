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
├── data/
│   ├── Models.kt                    # 数据模型
│   ├── ProfitAnalysisModels.kt      # 盈亏分析模型
│   ├── ExchangeRates.kt             # 汇率数据
│   ├── SampleData.kt                # 示例数据
│   ├── local/
│   │   ├── StockLedgerDatabase.kt   # Room 数据库
│   │   ├── LedgerDao.kt             # 数据访问对象
│   │   ├── TransactionEntity.kt     # 交易实体
│   │   └── QuoteSnapshotEntity.kt   # 行情快照实体
│   └── repository/
│       ├── LedgerRepository.kt      # 数据仓库
│       └── ExchangeRateDataSource.kt # 汇率数据源
└── ui/
    ├── StockLedgerApp.kt            # 应用主入口 Composable
    ├── Screens.kt                   # 页面组件
    ├── ProfitAnalysisScreen.kt      # 盈亏分析页面
    ├── Components.kt                # 通用 UI 组件
    ├── LedgerViewModel.kt           # ViewModel
    └── theme/
        ├── Color.kt                 # 颜色定义
        ├── Theme.kt                 # 主题配置
        └── Type.kt                  # 字体排版
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

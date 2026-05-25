# Stock Ledger Developer & AI Agent Guide (AGENT.md)

This document contains essential compilation, testing, deployment, and environment information for developers and AI coding agents working on the Stock Ledger Android application.

---

## 1. Environment Configurations (本地开发环境配置)

To compile and build this project locally, ensure the following paths are set as environment variables (or configured inline before commands).

| Environment Variable | Local Path | Description |
| :--- | :--- | :--- |
| **`JAVA_HOME`** | `D:\Software\Program\Android Studio\jbr` | JetBrains Runtime (Java JDK) bundled with Android Studio. |
| **`ANDROID_HOME`** | `D:\ProgramData\Android\Sdk` | Android SDK path. |

### Inline Execution Examples (PowerShell)
```powershell
# Set environment variables and compile Kotlin code
$env:JAVA_HOME="D:\Software\Program\Android Studio\jbr"; $env:ANDROID_HOME="D:\ProgramData\Android\Sdk"; .\gradlew :app:compileDebugKotlin

# Set environment variables and run unit tests
$env:JAVA_HOME="D:\Software\Program\Android Studio\jbr"; $env:ANDROID_HOME="D:\ProgramData\Android\Sdk"; .\gradlew :app:testDebugUnitTest
```

---

## 2. Useful Gradle Commands (常用 Gradle 编译构建命令)

Always run Gradle commands from the root directory `D:\Project\recoder`.

| Task Description | Command |
| :--- | :--- |
| **Compile Kotlin** | `.\gradlew :app:compileDebugKotlin` |
| **Run Unit Tests** | `.\gradlew :app:testDebugUnitTest` |
| **Build & Install (Debug)** | `.\gradlew :app:installDebug` |
| **Clean Build** | `.\gradlew clean` |

---

## 3. Deployment & ADB Debugging (部署与无线调试)

The application supports debugging on connected physical devices or emulators.

* **Debug Package Name**: `com.recoder.stockledger.debug` (due to `applicationIdSuffix = ".debug"` in build.gradle)
* **Main Activity**: `com.recoder.stockledger.MainActivity`

### Launching the Application
```bash
adb shell am start -n com.recoder.stockledger.debug/com.recoder.stockledger.MainActivity
```

### Inspecting the App Sandbox Database
Since the debug variant is running, you can access the SQLite database directly inside the secure sandbox using `run-as`:

```bash
# Enter the application data sandbox
adb shell run-as com.recoder.stockledger.debug

# View database files
ls -lh /data/data/com.recoder.stockledger.debug/databases/

# SQLite Database Location
# /data/data/com.recoder.stockledger.debug/databases/stock-ledger.db
```

### Backups Path
Backups exported by the application are usually saved under:
* `/sdcard/Download/` (e.g. `stock-ledger-backup-YYYYMMDD-HHMMSS.json`)

---

## 4. Architecture & Data Layers Checklist (系统架构与数据分区)
* **Ledgers (`ledgers` table)**: Represents the top-level boundary for data isolation.
  * `type = "PERSONAL"`: Individual portfolios.
  * `type = "JOINT"`: Multi-investor portfolios supporting dynamic contribution ratios.
* **Transactions (`transactions` table)**: References `ledgerId` to partition trades.
  * Room Migration `MIGRATION_3_4` automatically populates existing records under the default ledger `1`.

# Release APK 发布规范

本文档记录 StockLedger 通过 GitHub Release 发布 APK 的固定流程。后续发布安装包时优先按本文档执行，不再重新探索 GitHub CLI、签名、打包、tag 和资产上传步骤。

## 发布前置条件

- 发布前必须先确认工作区和分支状态：
  ```bash
  git status --short --branch
  ```
- 正式 Release APK 必须使用 release keystore 签名，不允许公开发布 debug 签名 APK。
- `local.properties`、`.jks`、签名密码、keystore 备份文件不得提交到 Git。
- 当前 release 签名配置从 `local.properties` 或环境变量读取以下字段：
  - `RECODER_STORE_FILE`
  - `RECODER_STORE_PASSWORD`
  - `RECODER_KEY_ALIAS`
  - `RECODER_KEY_PASSWORD`
- release keystore 必须长期保存并私下备份。后续同一个应用 ID 的升级包必须继续使用同一个 keystore。

## 版本与命名规则

- `versionName` 必须使用语义版本：`X.Y.Z`，例如 `1.2.0`。
- `versionCode` 必须单调递增，每次对外发布都要比上一个正式版本大。
- Git tag 必须为 `vX.Y.Z`，并与 `versionName` 完全对应。
  - `versionName = "1.2.0"` 对应 tag `v1.2.0`。
- GitHub Release title 使用 `StockLedger X.Y.Z`。
- GitHub Release APK 资产名固定为 `StockLedger-X.Y.Z.apk`。
- App 内自动更新功能会优先匹配 `StockLedger-*.apk`，因此不要把正式资产命名为 `app-release.apk`、`debug.apk`、`latest.apk` 或 `nightly.apk`。
- 不要把 `latest`、`nightly`、`test` 这类非语义版本 tag 作为正式更新发布。

## 提交与分支规则

- 功能开发在功能分支完成，验证通过后再合并到 `master`。
- 发布版本号调整应作为明确提交进入发布范围，提交内容至少包含：
  - `app/build.gradle.kts` 中的 `versionCode`
  - `app/build.gradle.kts` 中的 `versionName`
- 合并到 `master` 前确认功能分支已经完成必要测试。
- `master` 推送到远端后再创建发布 tag，避免 tag 指向未发布到远端的提交。
- 一个正式版本只对应一个 GitHub Release 和一个语义版本 tag。
- 如果 Release 已发布但 APK 资产需要重传，只允许在同一个 tag 下使用 `gh release upload --clobber` 覆盖资产；不要临时改 tag 名绕过问题。

## 发布流程

以下命令中的 `X.Y.Z` 需要替换为本次版本号，例如 `1.2.0`。

### 1. 检查版本号

确认 `app/build.gradle.kts`：

```kotlin
versionCode = 3
versionName = "1.2.0"
```

规则：

- `versionCode` 大于上一版。
- `versionName` 与计划 tag `vX.Y.Z` 一致。

### 2. 运行测试

```bash
.\gradlew.bat --no-daemon test
```

如果测试失败，不发布 APK。

### 3. 构建 release APK

```bash
.\gradlew.bat --no-daemon assembleRelease
```

默认输出路径：

```text
app\build\outputs\apk\release\app-release.apk
```

如果 release 签名参数缺失，构建会失败并提示缺少的 `RECODER_*` 字段。

### 4. 验证 APK 签名

使用 Android SDK build-tools 中的 `apksigner` 验证：

```bash
C:\Users\Administrator\AppData\Local\Android\Sdk\build-tools\37.0.0\apksigner.bat verify --verbose app\build\outputs\apk\release\app-release.apk
```

验收要求：

- 输出包含 `Verifies`。
- 至少有一个 APK Signature Scheme 验证为 `true`。
- `Number of signers: 1`。

### 5. 准备 GitHub Release 资产名

把 APK 复制为固定资产名：

```bash
copy app\build\outputs\apk\release\app-release.apk %USERPROFILE%\Downloads\StockLedger-X.Y.Z.apk
```

示例：

```bash
copy app\build\outputs\apk\release\app-release.apk %USERPROFILE%\Downloads\StockLedger-1.2.0.apk
```

### 6. 推送 master

确认即将推送的提交：

```bash
git log --oneline origin/master..master
```

推送：

```bash
git push origin master
```

### 7. 创建并推送 tag

```bash
git tag -a vX.Y.Z -m StockLedger-X.Y.Z
git push origin vX.Y.Z
```

示例：

```bash
git tag -a v1.2.0 -m StockLedger-1.2.0
git push origin v1.2.0
```

### 8. 发布 GitHub Release

先确认 GitHub CLI 登录状态：

```bash
gh auth status
```

当前 Windows 环境已系统级安装 GitHub CLI。新终端通常可以直接使用 `gh`；如果当前 shell 尚未刷新 PATH，可以使用：

```bash
C:\Progra~1\GITHUB~1\gh.exe
```

创建 Release 并上传 APK：

```bash
gh release create vX.Y.Z %USERPROFILE%\Downloads\StockLedger-X.Y.Z.apk --repo xxofficial/recoder --verify-tag --title "StockLedger X.Y.Z" --generate-notes
```

示例：

```bash
gh release create v1.2.0 %USERPROFILE%\Downloads\StockLedger-1.2.0.apk --repo xxofficial/recoder --verify-tag --title "StockLedger 1.2.0" --generate-notes
```

如果 Release 已存在，只覆盖 APK 资产：

```bash
gh release upload vX.Y.Z %USERPROFILE%\Downloads\StockLedger-X.Y.Z.apk --repo xxofficial/recoder --clobber
```

## 发布后验证

查看 Release 和资产：

```bash
gh release view vX.Y.Z --repo xxofficial/recoder --json url,tagName,name,assets
```

验收要求：

- `tagName` 是 `vX.Y.Z`。
- `name` 是 `StockLedger X.Y.Z`。
- assets 中存在 `StockLedger-X.Y.Z.apk`。
- asset `contentType` 是 `application/vnd.android.package-archive`。
- asset `size` 与本地 APK 大小一致。

也可以打开发布页人工确认：

```text
https://github.com/xxofficial/recoder/releases/tag/vX.Y.Z
```

## 自动更新约束

App 内自动更新功能依赖 GitHub Release 的以下约定：

- 查询目标仓库固定为 `xxofficial/recoder`。
- 最新版本来自 GitHub latest release。
- 正式 tag 必须是 `vX.Y.Z`。
- APK asset 必须是 `.apk` 文件。
- 优先资产名是 `StockLedger-X.Y.Z.apk`。
- 如果 Release 中有多个 APK，自动更新会优先选择 `StockLedger-*.apk`。

因此正式 Release 中不要放入：

- debug APK
- 测试 APK
- 未签名 APK
- 非正式签名 APK
- 多个同版本含义不清的 APK

## 常见问题

### gh 命令不可用

先尝试打开新终端。如果当前 shell 仍找不到 `gh`，使用短路径：

```bash
C:\Progra~1\GITHUB~1\gh.exe --version
```

### gh 未登录

执行：

```bash
gh auth login --hostname github.com --web --git-protocol https --scopes repo
```

按 CLI 提示打开 GitHub 设备授权页面并完成登录。

### Release 创建时提示 tag 不存在

说明 tag 没有推到远端。执行：

```bash
git push origin vX.Y.Z
```

再重试 `gh release create`。

### APK 资产名传错

不要保留 `app-release.apk` 作为正式资产名。重新上传正确命名资产：

```bash
gh release upload vX.Y.Z %USERPROFILE%\Downloads\StockLedger-X.Y.Z.apk --repo xxofficial/recoder --clobber
```

如果旧资产已经发布，可在 GitHub Release 页面删除旧资产，或使用：

```bash
gh release delete-asset vX.Y.Z app-release.apk --repo xxofficial/recoder -y
```

## 发布检查清单

- [ ] `versionCode` 已递增。
- [ ] `versionName` 是 `X.Y.Z`。
- [ ] tag 计划为 `vX.Y.Z`。
- [ ] 工作区没有无关改动。
- [ ] `test` 通过。
- [ ] `assembleRelease` 通过。
- [ ] APK 签名验证通过。
- [ ] APK 资产名为 `StockLedger-X.Y.Z.apk`。
- [ ] `master` 已推送。
- [ ] tag 已推送。
- [ ] GitHub Release 已创建。
- [ ] Release 中只有正式签名 APK 作为安装资产。

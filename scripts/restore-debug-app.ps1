param(
    [string]$AdbPath = "C:\Users\Administrator\AppData\Local\Android\Sdk\platform-tools\adb.exe",
    [string]$PackageName = "com.recoder.stockledger.debug",
    [string]$BackupDir = "E:\AndroidWorkSpace\recoder\backups\phone-prod"
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $AdbPath)) {
    throw "adb not found at $AdbPath"
}

$requiredFiles = @(
    "stock-ledger.db",
    "stock-ledger.db-wal",
    "stock-ledger.db-shm"
)

foreach ($file in $requiredFiles) {
    $path = Join-Path $BackupDir $file
    if (-not (Test-Path $path)) {
        throw "Backup file missing: $path"
    }
}

& $AdbPath shell monkey -p $PackageName -c android.intent.category.LAUNCHER 1 | Out-Null
Start-Sleep -Seconds 2
& $AdbPath shell am force-stop $PackageName | Out-Null

$remoteTmpDir = "/data/local/tmp/stockledger-restore"
& $AdbPath shell "rm -rf $remoteTmpDir && mkdir -p $remoteTmpDir" | Out-Null

foreach ($file in $requiredFiles) {
    & $AdbPath push (Join-Path $BackupDir $file) "$remoteTmpDir/$file" | Out-Null
}

$copyScript = @"
mkdir -p databases
cp $remoteTmpDir/stock-ledger.db databases/stock-ledger.db
cp $remoteTmpDir/stock-ledger.db-wal databases/stock-ledger.db-wal
cp $remoteTmpDir/stock-ledger.db-shm databases/stock-ledger.db-shm
"@

& $AdbPath shell run-as $PackageName sh -c $copyScript
& $AdbPath shell "rm -rf $remoteTmpDir" | Out-Null

Write-Output "Restore finished for $PackageName"

param(
    [string]$AdbPath = "C:\Users\Administrator\AppData\Local\Android\Sdk\platform-tools\adb.exe",
    [string]$PackageName = "com.recoder.stockledger",
    [string]$OutputDir = "E:\AndroidWorkSpace\recoder\backups\phone-prod"
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $AdbPath)) {
    throw "adb not found at $AdbPath"
}

New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null

& $AdbPath shell am force-stop $PackageName | Out-Null

function Copy-RunAsFile {
    param(
        [string]$RemotePath,
        [string]$LocalPath
    )

    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = $AdbPath
    $psi.Arguments = "exec-out run-as $PackageName cat $RemotePath"
    $psi.RedirectStandardOutput = $true
    $psi.UseShellExecute = $false

    $process = [System.Diagnostics.Process]::Start($psi)
    $stream = New-Object System.IO.MemoryStream
    $process.StandardOutput.BaseStream.CopyTo($stream)
    $process.WaitForExit()

    if ($process.ExitCode -ne 0) {
        throw "Failed to copy $RemotePath from $PackageName"
    }

    [System.IO.File]::WriteAllBytes($LocalPath, $stream.ToArray())
}

Copy-RunAsFile "databases/stock-ledger.db" (Join-Path $OutputDir "stock-ledger.db")
Copy-RunAsFile "databases/stock-ledger.db-wal" (Join-Path $OutputDir "stock-ledger.db-wal")
Copy-RunAsFile "databases/stock-ledger.db-shm" (Join-Path $OutputDir "stock-ledger.db-shm")

Get-ChildItem $OutputDir | Select-Object Name, Length, LastWriteTime

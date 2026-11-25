param(
    [Parameter(Mandatory = $true)] [string]$Apk,
    [Parameter(Mandatory = $true)] [string]$Serial
)

$ErrorActionPreference = 'Stop'
$localAdb = Join-Path $PSScriptRoot '../../../构建数据/sdk/platform-tools/adb.exe'
$adb = if (Test-Path -LiteralPath $localAdb) {
    (Resolve-Path -LiteralPath $localAdb).Path
} else {
    (Get-Command adb.exe -ErrorAction Stop).Source
}
$packageFile = Get-Item -LiteralPath $Apk
if ($packageFile.Extension -ne '.apk') { throw '请选择 APK 文件。' }
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [IO.Compression.ZipFile]::OpenRead($packageFile.FullName)
try {
    if (!$archive.GetEntry('AndroidManifest.xml') -or !$archive.GetEntry('classes.dex')) {
        throw '安装包缺少 Android 应用文件，请重新下载 APK。'
    }
} finally {
    $archive.Dispose()
}

Write-Output "APK SHA-256：$((Get-FileHash -LiteralPath $packageFile.FullName -Algorithm SHA256).Hash)"
$state = & $adb -s $Serial get-state 2>&1
if ($LASTEXITCODE -ne 0 -or $state -ne 'device') { throw '手表未连接或未允许 USB 调试。' }
$result = & $adb -s $Serial install -r $packageFile.FullName 2>&1
$installExitCode = $LASTEXITCODE
$result | Write-Output
if ($installExitCode -eq 0) { exit 0 }

$detail = $result -join [Environment]::NewLine
$message = switch -Regex ($detail) {
    'INSTALL_FAILED_UPDATE_INCOMPATIBLE|INSTALL_PARSE_FAILED_INCONSISTENT_CERTIFICATES' {
        '设备已有同包名、签名不同的应用。请确认是否安装了官方 QQ 音乐，或其他来源的同名版本。需卸载冲突应用后再安装，卸载会清除它的本地数据。'
        break
    }
    'INSTALL_FAILED_VERSION_DOWNGRADE' {
        '设备已有版本代码更高的同包名应用。请使用本项目的较新修订包，或核对现有应用来源。'
        break
    }
    'INSTALL_FAILED_INSUFFICIENT_STORAGE' {
        '手表存储空间不足，请释放空间后重试。'
        break
    }
    'INSTALL_PARSE_FAILED|INSTALL_FAILED_INVALID_APK|INSTALL_FAILED_NO_CERTIFICATES' {
        '系统无法校验安装包，请核对 SHA-256 并重新下载。'
        break
    }
    default { '请保留上方 Failure 中的具体错误码，供进一步定位。' }
}
throw $message

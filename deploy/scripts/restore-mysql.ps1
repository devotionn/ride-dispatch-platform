param(
    [string]$ContainerName = 'ride-dispatch-mysql',
    [string]$DatabaseName = 'ride_dispatch',
    [string]$RootPassword = $env:MYSQL_ROOT_PASSWORD,
    [Parameter(Mandatory = $true)]
    [string]$BackupFile,
    [switch]$ConfirmRestore
)

$ErrorActionPreference = 'Stop'

if (-not $ConfirmRestore) {
    throw '恢复会覆盖目标数据库，请显式传入 -ConfirmRestore。'
}
if ([string]::IsNullOrWhiteSpace($RootPassword)) {
    throw '请通过 -RootPassword 或 MYSQL_ROOT_PASSWORD 提供 MySQL root 密码。'
}
$BackupFile = [IO.Path]::GetFullPath($BackupFile)
if (-not (Test-Path -LiteralPath $BackupFile -PathType Leaf)) {
    throw "备份文件不存在：$BackupFile"
}

$running = docker inspect --format '{{.State.Running}}' $ContainerName 2>$null
if ($LASTEXITCODE -ne 0 -or $running.Trim() -ne 'true') {
    throw "MySQL 容器不可用：$ContainerName"
}

Get-Content -LiteralPath $BackupFile -Raw | docker exec -i $ContainerName mysql "-uroot" "-p$RootPassword" $DatabaseName
if ($LASTEXITCODE -ne 0) {
    throw "MySQL 恢复失败：$BackupFile"
}

Write-Output "MySQL 恢复完成：$BackupFile -> $DatabaseName"

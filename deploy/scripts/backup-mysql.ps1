param(
    [string]$ContainerName = 'ride-dispatch-mysql',
    [string]$DatabaseName = 'ride_dispatch',
    [string]$RootPassword = $env:MYSQL_ROOT_PASSWORD,
    [string]$OutputFile
)

$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($RootPassword)) {
    throw '请通过 -RootPassword 或 MYSQL_ROOT_PASSWORD 提供 MySQL root 密码。'
}

if ([string]::IsNullOrWhiteSpace($OutputFile)) {
    $backupDir = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\backups'))
    New-Item -ItemType Directory -Path $backupDir -Force | Out-Null
    $OutputFile = Join-Path $backupDir ("{0}-{1}.sql" -f $DatabaseName, (Get-Date -Format 'yyyyMMdd-HHmmss'))
} else {
    $OutputFile = [IO.Path]::GetFullPath($OutputFile)
    New-Item -ItemType Directory -Path (Split-Path -Parent $OutputFile) -Force | Out-Null
}

$running = docker inspect --format '{{.State.Running}}' $ContainerName 2>$null
if ($LASTEXITCODE -ne 0 -or $running.Trim() -ne 'true') {
    throw "MySQL 容器不可用：$ContainerName"
}

docker exec $ContainerName mysqldump "-uroot" "-p$RootPassword" '--single-transaction' '--routines' '--events' $DatabaseName | Out-File -LiteralPath $OutputFile -Encoding utf8
if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $OutputFile)) {
    throw "MySQL 备份失败：$OutputFile"
}

Write-Output "MySQL 备份已生成：$OutputFile"

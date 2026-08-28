[CmdletBinding(SupportsShouldProcess)]
param(
    [int]$Port = 8081
)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$jarPath = (Join-Path $projectRoot 'server\target\ride-dispatch-server-0.1.0-SNAPSHOT.jar').ToLowerInvariant()
$processes = Get-CimInstance Win32_Process | Where-Object {
    $_.Name -match '^java(w)?\.exe$' -and $_.CommandLine -and $_.CommandLine.ToLowerInvariant().Contains($jarPath)
}

foreach ($process in $processes) {
    if ($PSCmdlet.ShouldProcess("PID $($process.ProcessId)", '停止 ride-dispatch 后端')) {
        Stop-Process -Id $process.ProcessId -Force
        Write-Host "已停止后端 PID=$($process.ProcessId)"
    }
}

$pidFile = Join-Path $projectRoot "server\target\local-runtime-$Port.pid"
if (Test-Path $pidFile) { Remove-Item -LiteralPath $pidFile -Force }
if (-not $processes) { Write-Host '未找到正在运行的 ride-dispatch 后端进程。' }

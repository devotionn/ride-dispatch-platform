[CmdletBinding()]
param(
    [int]$Port = 8081,
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$JarPath,
    [int]$WaitSeconds = 60
)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$serverRoot = Join-Path $projectRoot 'server'
$targetRoot = Join-Path $serverRoot 'target'
if (-not $JarPath) { $JarPath = Join-Path $targetRoot 'ride-dispatch-server-0.1.0-SNAPSHOT.jar' }
if (-not (Test-Path -LiteralPath $JarPath)) { throw "找不到后端 jar：$JarPath。请先运行 build-local.ps1。" }
if (-not $JavaHome) {
    $javaCommand = Get-Command java.exe -ErrorAction SilentlyContinue
    if ($javaCommand) { $JavaHome = Split-Path (Split-Path $javaCommand.Source -Parent) -Parent }
}
$java = if ($JavaHome -and (Test-Path (Join-Path $JavaHome 'bin\java.exe'))) { Join-Path $JavaHome 'bin\java.exe' } else { (Get-Command java.exe -ErrorAction SilentlyContinue)?.Source }
if (-not $java) { throw '未找到 Java 21。请设置 JAVA_HOME。' }
$portInUse = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
if ($portInUse) { throw "端口 $Port 已被占用。请先运行 stop-local.ps1 或改用其他端口。" }
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$stdout = Join-Path $targetRoot "local-runtime-$Port-$stamp.out.log"
$stderr = Join-Path $targetRoot "local-runtime-$Port-$stamp.err.log"
$process = Start-Process -FilePath $java -ArgumentList @('-jar', $JarPath, '--spring.profiles.active=local', "--server.port=$Port") -WorkingDirectory $serverRoot -RedirectStandardOutput $stdout -RedirectStandardError $stderr -PassThru
$pidFile = Join-Path $targetRoot "local-runtime-$Port.pid"
Set-Content -LiteralPath $pidFile -Value $process.Id -Encoding ascii
$deadline = [DateTime]::UtcNow.AddSeconds($WaitSeconds)
$healthy = $false
while ([DateTime]::UtcNow -lt $deadline) {
    Start-Sleep -Seconds 1
    try {
        $health = Invoke-RestMethod -Uri "http://localhost:$Port/actuator/health" -Method Get -TimeoutSec 3
        if ($health.status -eq 'UP') { $healthy = $true; break }
    } catch { }
}
if (-not $healthy) { throw "后端在 $WaitSeconds 秒内未通过健康检查，详见日志：$stdout" }
Write-Host "后端已启动并通过健康检查：PID=$($process.Id)，地址=http://localhost:$Port"
Write-Host "日志：$stdout"

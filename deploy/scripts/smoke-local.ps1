[CmdletBinding()]
param(
    [string]$ApiUrl = 'http://localhost:8081'
)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$health = Invoke-RestMethod -Uri "$($ApiUrl.TrimEnd('/'))/actuator/health" -Method Get
if ($health.status -ne 'UP') { throw "后端健康检查失败：$($health | ConvertTo-Json -Compress)" }
Write-Host "健康检查通过：$ApiUrl"

$node = (Get-Command node.exe -ErrorAction SilentlyContinue)?.Source
if (-not $node) { throw '未找到 Node.js，无法运行 HTTP 深度冒烟。' }
$env:API_URL = $ApiUrl.TrimEnd('/')
Push-Location (Join-Path $projectRoot 'e2e')
try {
    & $node 'local-http-depth.cjs'
    if ($LASTEXITCODE -ne 0) { throw "HTTP depth failed with exit code $LASTEXITCODE" }
} finally {
    Pop-Location
}

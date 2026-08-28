[CmdletBinding()]
param(
    [switch]$SkipTests,
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$MavenHome = $env:MAVEN_HOME
)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path

if (-not $JavaHome) {
    $javaCommand = Get-Command java.exe -ErrorAction SilentlyContinue
    if ($javaCommand) { $JavaHome = Split-Path (Split-Path $javaCommand.Source -Parent) -Parent }
}
if (-not $JavaHome -or -not (Test-Path (Join-Path $JavaHome 'bin\java.exe'))) {
    throw '未找到 Java 21。请设置 JAVA_HOME 指向 JDK 21。'
}
$env:JAVA_HOME = (Resolve-Path $JavaHome).Path
$env:Path = "$(Join-Path $env:JAVA_HOME 'bin');$env:Path"

if (-not $MavenHome) {
    $mavenCommand = Get-Command mvn.cmd -ErrorAction SilentlyContinue
    if ($mavenCommand) { $MavenHome = Split-Path (Split-Path $mavenCommand.Source -Parent) -Parent }
}
$maven = if ($MavenHome -and (Test-Path (Join-Path $MavenHome 'bin\mvn.cmd'))) {
    Join-Path $MavenHome 'bin\mvn.cmd'
} else {
    (Get-Command mvn.cmd -ErrorAction SilentlyContinue)?.Source
}
if (-not $maven) { throw '未找到 Maven 3.9+。请设置 MAVEN_HOME 或将 mvn.cmd 加入 PATH。' }

$goal = if ($SkipTests) { 'package' } else { 'verify' }
$mavenArguments = @('-f', 'server/pom.xml', $goal)
if ($SkipTests) { $mavenArguments += '-DskipTests' }
Write-Host "Building backend with Java $($env:JAVA_HOME): mvn $($mavenArguments -join ' ')"
Push-Location $projectRoot
try {
    & $maven @mavenArguments
    if ($LASTEXITCODE -ne 0) { throw "Maven $goal failed with exit code $LASTEXITCODE" }
} finally {
    Pop-Location
}

[CmdletBinding()]
param(
    [string]$ApiBaseUrl = 'http://10.0.2.2:8080',
    [switch]$Install
)

$ErrorActionPreference = 'Stop'

$jdk = Get-ChildItem -Directory 'C:\Program Files\Eclipse Adoptium' -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -like 'jdk-17*' } |
    Sort-Object Name -Descending |
    Select-Object -First 1
if (-not $jdk -or -not (Test-Path (Join-Path $jdk.FullName 'bin\java.exe'))) {
    throw 'Temurin JDK 17 was not found under C:\Program Files\Eclipse Adoptium.'
}

$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { 'D:\dev_tool\Android\Sdk' }
if (-not (Test-Path (Join-Path $sdk 'platform-tools\adb.exe'))) {
    throw "Android SDK platform-tools was not found at $sdk. Set ANDROID_HOME first."
}

$env:JAVA_HOME = $jdk.FullName
$env:ANDROID_HOME = $sdk
$env:ANDROID_SDK_ROOT = $sdk
$avdCandidates = @(
    $env:ANDROID_AVD_HOME,
    (Join-Path (Split-Path $sdk -Parent) 'Avd'),
    (Join-Path $env:USERPROFILE '.android\avd')
) | Where-Object { $_ -and (Test-Path $_) }
if ($avdCandidates) {
    $env:ANDROID_AVD_HOME = $avdCandidates[0]
}

& (Join-Path $env:JAVA_HOME 'bin\java.exe') -version
if ($LASTEXITCODE -ne 0) { throw 'JDK 17 could not be executed.' }

$tasks = @(':app:testDebugUnitTest', ':app:assembleDebug')
if ($Install) { $tasks += ':app:installDebug' }
& "$PSScriptRoot\..\gradlew.bat" -p "$PSScriptRoot\.." @tasks "-PdriverApiBaseUrl=$ApiBaseUrl" --no-daemon --console=plain
if ($LASTEXITCODE -ne 0) { throw "Android verification failed with exit code $LASTEXITCODE." }

# Builds + installs the NepalGo debug APK on the running emulator/device.
#
# Compile-time config (API_BASE, FIREBASE_API_KEY) comes from dart_defines.json (gitignored),
# fed to Flutter with --dart-define-from-file. Like a bank terminal's config card: the same
# app binary, different settings per environment, and the card never goes into the repo.
#
# One-time setup:  copy dart_defines.example.json dart_defines.json  -> paste the Firebase web key
# (Firebase console -> Project settings -> General -> Web API key).
# Usage:           .\build-android.ps1            (add -Fresh to wipe app data, e.g. to see onboarding again)
param([switch]$Fresh)
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

if (-not (Test-Path dart_defines.json)) {
    Write-Error "dart_defines.json missing. Run: copy dart_defines.example.json dart_defines.json  then paste your Firebase web API key."
}
$cfg = Get-Content dart_defines.json -Raw | ConvertFrom-Json
if (-not $cfg.FIREBASE_API_KEY -or $cfg.FIREBASE_API_KEY -like 'PASTE_*') {
    Write-Error "FIREBASE_API_KEY in dart_defines.json is not filled in. Login would fail - refusing to build."
}

$flutter = if (Get-Command flutter -ErrorAction SilentlyContinue) { 'flutter' } else { 'C:\src\flutter\bin\flutter.bat' }
$adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'

& $flutter build apk --debug --dart-define-from-file=dart_defines.json
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& $adb install -r build\app\outputs\flutter-apk\app-debug.apk
if ($Fresh) { & $adb shell pm clear com.nepalgo.app }
& $adb shell monkey -p com.nepalgo.app -c android.intent.category.LAUNCHER 1 | Out-Null
Write-Host "NepalGo installed and launched (API_BASE=$($cfg.API_BASE))."

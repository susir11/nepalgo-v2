# Builds NepalGo for Android.
#
# Compile-time config (API_BASE, FIREBASE_API_KEY) comes from a gitignored JSON file fed to
# Flutter with --dart-define-from-file. Like a bank terminal's config card: the same app
# binary, different settings per environment, and the card never goes into the repo.
#
# DEBUG (default): builds the debug APK from dart_defines.json, installs + launches it on the
#   running emulator/device.
#   One-time setup: copy dart_defines.example.json dart_defines.json -> paste the Firebase web key
#   .\build-android.ps1            (add -Fresh to wipe app data, e.g. to see onboarding again)
#
# RELEASE (-Release): builds the Play Store bundle (.aab) from dart_defines.prod.json, signed
#   with the upload key from android\key.properties, and REFUSES to finish if anything would
#   make a bad store upload (emulator/localhost API, plain http, missing key, debug signature).
#   One-time setup: copy dart_defines.example.json dart_defines.prod.json -> set
#   API_BASE to https://<your api domain> and the PRODUCTION Firebase web key; create
#   android\key.properties (see docs\APP-STORE-CHECKLIST.md).
#   .\build-android.ps1 -Release
param([switch]$Fresh, [switch]$Release)
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

$configFile = if ($Release) { 'dart_defines.prod.json' } else { 'dart_defines.json' }
if (-not (Test-Path $configFile)) {
    Write-Error "$configFile missing. Run: copy dart_defines.example.json $configFile  then fill it in."
}
$cfg = Get-Content $configFile -Raw | ConvertFrom-Json
if (-not $cfg.FIREBASE_API_KEY -or $cfg.FIREBASE_API_KEY -like 'PASTE_*') {
    Write-Error "FIREBASE_API_KEY in $configFile is not filled in. Login would fail - refusing to build."
}

$flutter = if (Get-Command flutter -ErrorAction SilentlyContinue) { 'flutter' } else { 'C:\src\flutter\bin\flutter.bat' }

if ($Release) {
    # Guard rails: each of these has shipped broken apps for somebody.
    $api = [string]$cfg.API_BASE
    if ($api -notmatch '^https://') { Write-Error "API_BASE must be https:// for a store build (got '$api')." }
    if ($api -match 'localhost|127\.0\.0\.1|10\.0\.2\.2') { Write-Error "API_BASE points at a local/emulator address ('$api')." }
    if (-not (Test-Path 'android\key.properties')) {
        Write-Error "android\key.properties missing: the bundle would be signed with the DEBUG key, which Google Play rejects."
    }

    & $flutter build appbundle --release "--dart-define-from-file=$configFile"
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    $aab = 'build\app\outputs\bundle\release\app-release.aab'

    # Verify the signature really is the upload key, not the debug fallback.
    $keytool = if ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME\bin\keytool.exe")) { "$env:JAVA_HOME\bin\keytool.exe" }
               else { (Get-ChildItem 'C:\Program Files\Java\jdk-*\bin\keytool.exe' | Select-Object -Last 1).FullName }
    $cert = & $keytool -printcert -jarfile $aab 2>&1 | Out-String
    if ($cert -match 'CN=Android Debug') { Write-Error "$aab is signed with the DEBUG key. Check android\key.properties." }
    $owner = ([regex]::Match($cert, 'Owner: (.+)')).Groups[1].Value.Trim()
    $sha256 = ([regex]::Match($cert, 'SHA256: ([0-9A-F:]+)')).Groups[1].Value
    Write-Host ""
    Write-Host "Store bundle ready: $aab"
    Write-Host "  API_BASE : $api"
    Write-Host "  Signed by: $owner"
    Write-Host "  SHA-256  : $sha256   (must match the upload key you registered)"
    Write-Host "Upload it in Play Console -> Test and release -> Internal testing."
    return
}

$adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
& $flutter build apk --debug "--dart-define-from-file=$configFile"
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& $adb install -r build\app\outputs\flutter-apk\app-debug.apk
if ($Fresh) { & $adb shell pm clear com.nepalgo.app }
# monkey prints its args to stderr; Windows PowerShell 5.1 + 'Stop' would treat that as a failure.
$ErrorActionPreference = 'Continue'
& $adb shell monkey -p com.nepalgo.app -c android.intent.category.LAUNCHER 1 2>$null | Out-Null
Write-Host "NepalGo installed and launched (API_BASE=$($cfg.API_BASE))."

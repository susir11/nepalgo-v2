# Saves whatever the running emulator/phone shows as a store screenshot.
#   .\tool\capture-screenshot.ps1 01-explore   -> docs\store-listing\screenshots\01-explore.png
# Tip: hide the debug banner and use a demo account with a generic name before capturing.
param([Parameter(Mandatory = $true)][string]$Name)
$ErrorActionPreference = 'Stop'
$adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
$outDir = Join-Path $PSScriptRoot '..\..\docs\store-listing\screenshots'
New-Item -ItemType Directory -Force $outDir | Out-Null
$out = Join-Path (Resolve-Path $outDir) "$Name.png"
& $adb shell screencap -p /sdcard/nepalgo-shot.png
& $adb pull /sdcard/nepalgo-shot.png $out | Out-Null
& $adb shell rm /sdcard/nepalgo-shot.png
Add-Type -AssemblyName System.Drawing
$img = [System.Drawing.Image]::FromFile($out)
Write-Host "Saved $out ($($img.Width) x $($img.Height))"
$img.Dispose()

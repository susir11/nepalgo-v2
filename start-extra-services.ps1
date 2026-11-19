# Starts legal-service (:8085), notification-service (:8086) and support-service (:8087)
# from the repo root, in the background, with low-memory JVM flags.
#
# Why a script and not IntelliJ run configs: these three are not registered as
# Maven projects in IntelliJ yet (File > New > Module from Existing Sources >
# <service>/pom.xml). Until they are, this is the one-command way to run them.
#
# Values from .env (if present) are passed to the services as environment
# variables. Nothing is printed. Every service also has local-dev defaults.
#
# Usage (PowerShell, repo root):
#   .\start-extra-services.ps1              # start whatever is not running yet
#   .\start-extra-services.ps1 -Build       # rebuild the JARs first (mvnw package -DskipTests)
#   .\start-extra-services.ps1 -Only legal  # just one of: legal, notification, support
#   .\start-extra-services.ps1 -Stop        # stop all three
# Logs: logs\<service>.log
#
# In-app support chat (spec 20 flag) is OFF unless IN_APP_SUPPORT_CHAT_ENABLED=true is
# set before starting, in .env or in the shell:  $env:IN_APP_SUPPORT_CHAT_ENABLED='true'
param([switch]$Build, [switch]$Stop, [ValidateSet('legal', 'notification', 'support')][string]$Only)
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

$services = [ordered]@{ legal = 8085; notification = 8086; support = 8087 }
if ($Only) { $services = [ordered]@{ $Only = $services[$Only] } }

function Get-ListenerPid([int]$port) {
    $c = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($c) { return $c.OwningProcess } else { return $null }
}

if ($Stop) {
    foreach ($name in $services.Keys) {
        $listenerPid = Get-ListenerPid $services[$name]
        if ($listenerPid) { Stop-Process -Id $listenerPid -Force; Write-Host "stopped $name-service (pid $listenerPid)" }
        else { Write-Host "$name-service was not running" }
    }
    return
}

# .env -> environment of the child processes (KEY=VALUE lines; comments/blank lines skipped).
if (Test-Path .env) {
    foreach ($line in Get-Content .env) {
        if ($line -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)$') {
            [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2].Trim().Trim('"'), 'Process')
        }
    }
}

New-Item -ItemType Directory -Force logs | Out-Null
foreach ($name in $services.Keys) {
    $svc = "$name-service"; $port = $services[$name]
    if (Get-ListenerPid $port) { Write-Host "$svc already running on :$port"; continue }
    if ($Build) {
        Push-Location $svc
        & .\mvnw.cmd -q package -DskipTests
        if ($LASTEXITCODE -ne 0) { Pop-Location; throw "build of $svc failed" }
        Pop-Location
    }
    $jar = "$svc\target\$svc-0.0.1-SNAPSHOT.jar"
    if (-not (Test-Path $jar)) { throw "$jar not found - run with -Build" }
    # -Duser.timezone=UTC: Postgres rejects Windows' "Asia/Katmandu" zone name.
    $javaArgs = @('-Xmx160m', '-XX:TieredStopAtLevel=1', '-XX:+UseSerialGC', '-Duser.timezone=UTC', '-jar', $jar)
    $p = Start-Process java -ArgumentList $javaArgs -WorkingDirectory $PSScriptRoot -WindowStyle Hidden `
        -RedirectStandardOutput "logs\$name.log" -RedirectStandardError "logs\$name.err.log" -PassThru
    Write-Host "starting $svc on :$port (pid $($p.Id)), log: logs\$name.log"
}
Write-Host "Services register with Eureka within ~30 s; the gateway routes to them after that."

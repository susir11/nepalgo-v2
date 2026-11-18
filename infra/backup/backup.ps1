# Nightly backup of the whole NepalGo database (all service schemas).
# Custom format (-Fc): compressed, and pg_restore can restore one schema or
# one table from it. Keeps the newest 14 dumps.
#   Local:  powershell -File infra\backup\backup.ps1
#   Prod:   use the managed Postgres provider's automated backups + PITR, and
#           still run restore-test.ps1 against a staging copy monthly (RUNBOOK).
$ErrorActionPreference = "Stop"
$dir = Join-Path $PSScriptRoot "..\..\backups"
New-Item -ItemType Directory -Force $dir | Out-Null
$file = Join-Path $dir ("nepalgo-" + (Get-Date -Format "yyyyMMdd-HHmmss") + ".dump")
docker exec nepalgo-postgres pg_dump -U nepalgo_admin -d nepalgo -Fc -f /tmp/nepalgo.dump
if ($LASTEXITCODE -ne 0) { throw "pg_dump failed" }
docker cp nepalgo-postgres:/tmp/nepalgo.dump $file
docker exec nepalgo-postgres rm /tmp/nepalgo.dump
Write-Host "Backup written: $file ($([math]::Round((Get-Item $file).Length/1KB)) KB)"
Get-ChildItem $dir -Filter "nepalgo-*.dump" | Sort-Object Name -Descending | Select-Object -Skip 14 | Remove-Item

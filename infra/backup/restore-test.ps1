# PROVES the latest backup can actually be restored (a backup you never
# restored is a hope, not a backup). Restores into a throw-away database
# "nepalgo_restore_test", then compares the row count of EVERY table against
# the live database, then drops the scratch database. Exit code 1 on mismatch.
$ErrorActionPreference = "Stop"
$dir = Join-Path $PSScriptRoot "..\..\backups"
$latest = Get-ChildItem $dir -Filter "nepalgo-*.dump" | Sort-Object Name -Descending | Select-Object -First 1
if (-not $latest) { throw "No backup found in $dir - run backup.ps1 first" }
Write-Host "Restoring $($latest.Name) into nepalgo_restore_test ..."
docker cp $latest.FullName nepalgo-postgres:/tmp/restore.dump
docker exec nepalgo-postgres dropdb -U nepalgo_admin --if-exists nepalgo_restore_test
docker exec nepalgo-postgres createdb -U nepalgo_admin nepalgo_restore_test
# --no-owner: roles are cluster-wide and already exist; ownership isn't the point of this test.
docker exec nepalgo-postgres pg_restore -U nepalgo_admin -d nepalgo_restore_test --no-owner --exit-on-error /tmp/restore.dump
if ($LASTEXITCODE -ne 0) { throw "pg_restore FAILED" }

$sql = @"
SELECT table_schema || '.' || table_name
FROM information_schema.tables
WHERE table_type = 'BASE TABLE' AND table_schema NOT IN ('pg_catalog','information_schema','public')
ORDER BY 1;
"@
$tables = docker exec nepalgo-postgres psql -U nepalgo_admin -d nepalgo -tA -c $sql
$bad = 0; $checked = 0
foreach ($t in $tables) {
  if (-not $t) { continue }
  $live = docker exec nepalgo-postgres psql -U nepalgo_admin -d nepalgo -tA -c "SELECT count(*) FROM $t"
  $rest = docker exec nepalgo-postgres psql -U nepalgo_admin -d nepalgo_restore_test -tA -c "SELECT count(*) FROM $t"
  $checked++
  if ($live -ne $rest) { $bad++; Write-Host ("MISMATCH {0}: live={1} restored={2}" -f $t, $live, $rest) -ForegroundColor Red }
}
docker exec nepalgo-postgres dropdb -U nepalgo_admin nepalgo_restore_test
docker exec nepalgo-postgres rm /tmp/restore.dump
if ($bad -gt 0) { Write-Host "RESTORE TEST FAILED: $bad of $checked tables differ" -ForegroundColor Red; exit 1 }
Write-Host "RESTORE TEST PASSED: $checked tables, row counts identical." -ForegroundColor Green

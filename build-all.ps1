# Rebuilds every service JAR (skip tests for speed; CI runs them).
# Run BEFORE `docker compose --profile app up -d --build` — the images copy the
# JAR from target/, so a stale JAR = a stale container (classic pitfall).
$ErrorActionPreference = "Stop"
$services = "eureka-server","api-gateway","auth-service","catalog-service","booking-service",
            "payment-service","legal-service","notification-service","support-service"
foreach ($s in $services) {
  Write-Host "== $s" -ForegroundColor Cyan
  Push-Location $s
  try { .\mvnw.cmd -q -DskipTests package; if ($LASTEXITCODE -ne 0) { throw "$s build failed" } }
  finally { Pop-Location }
}
Write-Host "All JARs built. Next: docker compose --profile app up -d --build" -ForegroundColor Green

# Read-only smoke test of the PUBLIC API through the gateway, using the Postman CLI.
# No login, no writes, no secrets: safe to run against production right after a deploy.
#
#   .\postman\smoke-public.ps1                                  # local stack (http://localhost:8080)
#   .\postman\smoke-public.ps1 -Gateway https://api.example.com # after a deploy
#
# Covers: catalogue list/search/detail/weather, current Terms/Privacy, Policy Review,
# onboarding + company content, the public HTML legal pages (incl. CSP header),
# and published destination reviews. Exit code 0 = every assertion passed.
param([string]$Gateway = 'http://localhost:8080', [int]$DestinationId = 1)
$ErrorActionPreference = 'Stop'
$collection = Join-Path $PSScriptRoot 'NepalGo.postman_collection.json'
$termsId = (Invoke-RestMethod "$Gateway/api/legal/terms/current").id   # "Published version by id" needs a real id
$folders = 'Catalog - Public (no auth)', 'Legal - Public (no auth)',
           'Public legal pages (HTML, no auth, via gateway)', 'Reviews - Public (no auth)'
$cli = @('collection', 'run', $collection)
foreach ($f in $folders) { $cli += @('-i', $f) }
foreach ($v in 'catalogUrl', 'legalUrl', 'bookingUrl', 'gatewayUrl') { $cli += @('--env-var', "$v=$Gateway") }
$cli += @('--env-var', "destinationId=$DestinationId", '--env-var', "termsDocumentId=$termsId")
& postman @cli
exit $LASTEXITCODE

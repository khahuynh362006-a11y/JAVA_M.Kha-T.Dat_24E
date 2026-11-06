$projectRoot=Split-Path $PSScriptRoot -Parent
$config=Get-Content -LiteralPath (Join-Path $projectRoot '.runtime/dev-secrets.json') -Raw | ConvertFrom-Json
Write-Output "Development accounts only:"
Write-Output "  admin@cineverse.local (ADMIN)"
Write-Output "  free@cineverse.local  (USER)"
Write-Output "  vip@cineverse.local   (USER with 30-day development VIP)"
Write-Output "Password: $($config.demoPassword)"

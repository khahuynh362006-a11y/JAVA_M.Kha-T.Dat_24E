param([switch]$Force)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
$target=Join-Path $projectRoot '.env'
if((Test-Path -LiteralPath $target) -and -not $Force){Write-Output '.env already exists; preserved.';return}
$configPath=Join-Path $projectRoot '.runtime/dev-secrets.json'
if(Test-Path -LiteralPath $configPath){$config=Get-Content -LiteralPath $configPath -Raw | ConvertFrom-Json}else{
  $config=@{dbPassword=[Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(24));rootPassword=[Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(24));jwtSecret=[Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(48));demoPassword=('Cv!'+[Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(8)))}
  New-Item -ItemType Directory -Path (Split-Path $configPath) -Force | Out-Null
  $config | ConvertTo-Json | Set-Content -LiteralPath $configPath -Encoding utf8
}
$text=Get-Content -LiteralPath (Join-Path $projectRoot '.env.example') -Raw
$text=$text.Replace("DB_PASSWORD=`n","DB_PASSWORD=$($config.dbPassword)`n").Replace("MYSQL_ROOT_PASSWORD=`n","MYSQL_ROOT_PASSWORD=$($config.rootPassword)`n").Replace("JWT_SECRET=`n","JWT_SECRET=$($config.jwtSecret)`n").Replace("DEMO_PASSWORD=`n","DEMO_PASSWORD=$($config.demoPassword)`n")
# Normalize CRLF before replacement on Windows checkouts.
foreach($entry in @{DB_PASSWORD=$config.dbPassword;MYSQL_ROOT_PASSWORD=$config.rootPassword;JWT_SECRET=$config.jwtSecret;DEMO_PASSWORD=$config.demoPassword}.GetEnumerator()){$text=[regex]::Replace($text,"(?m)^$($entry.Key)=\r?$","$($entry.Key)=$($entry.Value)")}
[IO.File]::WriteAllText($target,$text)
Write-Output '.env created with random local secrets. Use scripts/show-demo.ps1 for demo account details.'

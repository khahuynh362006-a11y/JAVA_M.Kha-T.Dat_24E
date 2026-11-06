param([switch]$Force)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
$mediaRoot=Join-Path $projectRoot 'demo-media'
New-Item -ItemType Directory -Path $mediaRoot -Force | Out-Null
$sources=@{
  'sintel.mp4'='https://media.w3.org/2010/05/sintel/trailer.mp4'
  'bunny.mp4'='https://media.w3.org/2010/05/bunny/trailer.mp4'
  'bunny-full.mp4'='https://media.w3.org/2010/05/bunny/movie.mp4'
}
foreach($name in $sources.Keys){
  $target=Join-Path $mediaRoot $name
  if((Test-Path -LiteralPath $target) -and -not $Force){continue}
  $partial="$target.download"
  & curl.exe --fail --location --retry 2 --max-time 180 --output $partial $sources[$name]
  if($LASTEXITCODE -ne 0){throw "Không tải được $name. Xem docs/MEDIA.md để cấu hình nguồn hợp pháp khác."}
  Move-Item -LiteralPath $partial -Destination $target -Force
}
Write-Output 'Media downloaded. Review docs/MEDIA.md for credits and exact content.'

param([switch]$CheckOnly)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
$publicRoot=Join-Path $projectRoot 'frontend/public'
$registry=Get-Content -LiteralPath (Join-Path $publicRoot 'art/poster-collection.json') -Raw | ConvertFrom-Json -AsHashtable
$excludedTitles=@('test','Phim kiểm thử E2E')
function ComparableForm($Form){
  $copy=$Form|ConvertTo-Json -Depth 15|ConvertFrom-Json -AsHashtable
  # Genres are a set in persistence; response order may differ between reads.
  $copy.genreIds=@($copy.genreIds|Sort-Object)
  return ($copy|ConvertTo-Json -Depth 15 -Compress)
}
if($registry.Count -ne 40){throw 'Bộ poster phải có đúng 40 phim gốc.'}
foreach($url in $registry.Values){
  if($url -notmatch '^/art/(posters/[a-z0-9-]+-v1\.png|poster-horizon-realistic-v1\.png)$'){throw 'Đường dẫn poster không hợp lệ.'}
  if(-not(Test-Path -LiteralPath (Join-Path $publicRoot $url.TrimStart('/')))){throw "Thiếu ảnh: $url"}
}
$base='http://localhost:8080/api/v1'
$origin='http://localhost:5174'
$demo=Get-Content -LiteralPath (Join-Path $projectRoot '.runtime/dev-secrets.json') -Raw | ConvertFrom-Json
$loginBody=@{email='admin@cineverse.local';password=$demo.demoPassword}|ConvertTo-Json
$session=Invoke-RestMethod "$base/auth/login" -Method Post -ContentType 'application/json' -Headers @{Origin=$origin} -Body $loginBody -SessionVariable browserSession
try{
  $headers=@{Authorization="Bearer $($session.accessToken)";Origin=$origin}
  $all=@();$page=0
  do{
    $list=Invoke-RestMethod "$base/admin/movies?page=$page&size=48" -Headers $headers
    $all+=@($list.content);$page++
  }while($page -lt $list.totalPages)
  $snapshot=@()
  foreach($movie in $all){$inputMovie=Invoke-RestMethod "$base/admin/movies/$($movie.id)" -Headers $headers;$snapshot+=@{id=$movie.id;input=$inputMovie}}
  $targets=@($snapshot|Where-Object {$registry.ContainsKey($_.input.slug) -and $_.input.title -notin $excludedTitles})
  $excluded=@($snapshot|Where-Object {$_.input.title -in $excludedTitles})
  if($targets.Count -ne 40 -or $excluded.Count -ne 2){throw 'Danh sách phim thay đổi; chưa gán ảnh vì không khớp 40 phim gốc và 2 phim được giữ nguyên.'}
  if($CheckOnly){Write-Output "Đủ 40 ảnh; tìm đúng 40 phim và 2 phim loại trừ. Chưa cập nhật dữ liệu.";return}
  $backupDirectory=Join-Path $projectRoot '.runtime/backups'
  New-Item -ItemType Directory -Path $backupDirectory -Force | Out-Null
  $backupPath=Join-Path $backupDirectory ('posters-'+(Get-Date -Format 'yyyyMMdd-HHmmss')+'.json')
  $snapshot|ConvertTo-Json -Depth 15|Set-Content -LiteralPath $backupPath -Encoding utf8
  $changed=0
  foreach($movie in $targets){
    # Read the latest form immediately before updating, preserving current metadata.
    $current=Invoke-RestMethod "$base/admin/movies/$($movie.id)" -Headers $headers
    if($current.title -in $excludedTitles){throw 'Không được sửa phim trong danh sách loại trừ.'}
    $poster=$registry[$current.slug]
    if(-not $poster){throw 'Slug phim đã thay đổi trong lúc cập nhật.'}
    if($current.poster -ne $poster){
      $current.poster=$poster
      $body=[Text.Encoding]::UTF8.GetBytes(($current|ConvertTo-Json -Depth 15))
      $null=Invoke-RestMethod "$base/admin/movies/$($movie.id)" -Method Put -ContentType 'application/json; charset=utf-8' -Headers $headers -Body $body
      $changed++
    }
    $verified=Invoke-RestMethod "$base/admin/movies/$($movie.id)" -Headers $headers
    if((ComparableForm $verified) -cne (ComparableForm $current)){throw "Dữ liệu phim $($movie.id) không khớp sau cập nhật."}
  }
  foreach($movie in $excluded){
    $current=Invoke-RestMethod "$base/admin/movies/$($movie.id)" -Headers $headers
    if((ComparableForm $current) -cne (ComparableForm $movie.input)){throw "Phim loại trừ $($movie.id) đã thay đổi."}
  }
  @{verified=40;changed=$changed;excludedUnchanged=2;backup=$backupPath;updatedAt=[DateTime]::UtcNow.ToString('o')}|ConvertTo-Json|Set-Content -LiteralPath (Join-Path $projectRoot '.runtime/poster-collection-applied.json') -Encoding utf8
  Write-Output "Đã gán $changed poster mới; kiểm tra đủ 40 phim và giữ nguyên 2 phim loại trừ."
}finally{
  $null=Invoke-RestMethod "$base/auth/logout" -Method Post -Headers @{Origin=$origin} -WebSession $browserSession
  $session=$null;$demo=$null;$loginBody=$null
}

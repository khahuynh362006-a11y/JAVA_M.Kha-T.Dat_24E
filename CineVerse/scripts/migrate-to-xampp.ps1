param([string]$XamppRoot='D:/xampp',[string]$SourceMySqlHome='D:/laragon/bin/mysql/mysql-8.4.3-winx64',[string]$Database='cineverse')
$ErrorActionPreference='Stop'
if($Database -notmatch '^[a-zA-Z][a-zA-Z0-9_]{0,40}$'){throw 'Tên database không hợp lệ.'}
$projectRoot=Split-Path $PSScriptRoot -Parent
. (Join-Path $PSScriptRoot 'import-env.ps1')
if($env:DB_PROVIDER -eq 'xampp'){throw 'Cấu hình đã dùng XAMPP. Không tự nhập lại hoặc ghi đè database.'}
& (Join-Path $PSScriptRoot 'start-xampp-mysql.ps1') -XamppRoot $XamppRoot
$sourceClient=Join-Path $SourceMySqlHome 'bin/mysql.exe';$dump=Join-Path $SourceMySqlHome 'bin/mysqldump.exe';$targetClient=Join-Path $XamppRoot 'mysql/bin/mysql.exe'
$sourcePassword=$env:DB_PASSWORD;$sourceUser=$env:DB_USER
function SourceSql([string]$Sql){$env:MYSQL_PWD=$sourcePassword;$result=& $sourceClient --no-defaults --default-character-set=utf8mb4 -h 127.0.0.1 -P 3307 -u $sourceUser --database=cineverse --batch --skip-column-names -e $Sql;if($LASTEXITCODE -ne 0){throw 'Không truy vấn được CSDL nguồn; chưa chuyển cấu hình.'};return $result}
function TargetSql([string]$Sql,[switch]$WithDatabase){Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue;$args=@('--no-defaults','--default-character-set=utf8mb4','-h','127.0.0.1','-P','3306','-u','root','--batch','--skip-column-names');if($WithDatabase){$args+="--database=$Database"};$result=& $targetClient @args -e $Sql;if($LASTEXITCODE -ne 0){throw 'Thao tác XAMPP thất bại. Không thay đổi dữ liệu nguồn; xem log và bản sao lưu.'};return $result}
try{
  $existing=TargetSql "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$Database'"
  if([long]$existing -gt 0){throw "Database $Database trong XAMPP đã có bảng. Không ghi đè. Chọn -Database tên_mới."}
  $tables=@(SourceSql 'SHOW TABLES')
  & (Join-Path $PSScriptRoot 'stop-local.ps1') -Services backend
  $backup=Join-Path $projectRoot ('.runtime/backups/xampp-'+(Get-Date -Format 'yyyyMMdd-HHmmss'))
  New-Item -ItemType Directory -Path $backup -Force|Out-Null
  Copy-Item -LiteralPath (Join-Path $projectRoot '.env') -Destination (Join-Path $backup 'source.env')
  $schema=Join-Path $backup 'schema-mysql.sql';$data=Join-Path $backup 'data.sql'
  $env:MYSQL_PWD=$sourcePassword
  $common=@('--no-defaults','--default-character-set=utf8mb4','--single-transaction','--no-tablespaces','--set-gtid-purged=OFF','--column-statistics=0','--skip-add-drop-table','--skip-comments','--skip-add-locks','--skip-disable-keys','-h','127.0.0.1','-P','3307','-u',$sourceUser)
  & $dump @common --no-data "--result-file=$schema" cineverse
  if($LASTEXITCODE -ne 0){throw 'Không export được schema nguồn.'}
  & $dump @common --no-create-info --complete-insert --hex-blob "--result-file=$data" cineverse
  if($LASTEXITCODE -ne 0){throw 'Không export được dữ liệu nguồn.'}
  $compatible=Join-Path $backup 'schema-xampp.sql'
  [IO.File]::WriteAllText($compatible,[IO.File]::ReadAllText($schema).Replace('utf8mb4_0900_ai_ci','utf8mb4_unicode_ci'),[Text.UTF8Encoding]::new($false))
  TargetSql "CREATE DATABASE IF NOT EXISTS $Database CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"|Out-Null
  TargetSql ("source "+$compatible.Replace('\','/')) -WithDatabase|Out-Null
  TargetSql ("source "+$data.Replace('\','/')) -WithDatabase|Out-Null
  $counts=[ordered]@{}
  foreach($table in $tables){if($table -notmatch '^[a-zA-Z0-9_]+$'){throw 'Tên bảng nguồn không hợp lệ.'};$source=[long](SourceSql "SELECT COUNT(*) FROM $table");$target=[long](TargetSql "SELECT COUNT(*) FROM $table" -WithDatabase);if($source -ne $target){throw "Số dòng bảng $table không khớp. Chưa đổi cấu hình."};$counts[$table]=$target}
  $dbUser='cineverse_app'
  if([long](TargetSql "SELECT COUNT(*) FROM mysql.user WHERE User='$dbUser'") -gt 0){$dbUser+='_'+[guid]::NewGuid().ToString('N').Substring(0,6)}
  $dbPassword=[Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(24))
  TargetSql "CREATE USER '$dbUser'@'localhost' IDENTIFIED BY '$dbPassword'; CREATE USER '$dbUser'@'127.0.0.1' IDENTIFIED BY '$dbPassword'; GRANT ALL PRIVILEGES ON $Database.* TO '$dbUser'@'localhost'; GRANT ALL PRIVILEGES ON $Database.* TO '$dbUser'@'127.0.0.1';"|Out-Null
  $settings=[ordered]@{DB_PROVIDER='xampp';XAMPP_ROOT=$XamppRoot;DB_DRIVER='org.mariadb.jdbc.Driver';DB_URL="jdbc:mariadb://127.0.0.1:3306/$Database`?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&preserveInstants=true";DB_USER=$dbUser;DB_PASSWORD=$dbPassword;ADMIN_URL='http://localhost:5174'}
  $envPath=Join-Path $projectRoot '.env';$lines=[Collections.Generic.List[string]]::new()
  foreach($line in [IO.File]::ReadAllLines($envPath)){if($line -match '^([A-Z][A-Z0-9_]*)=' -and $settings.Contains($matches[1])){continue};$lines.Add($line)}
  foreach($key in $settings.Keys){$lines.Add("$key=$($settings[$key])")}
  [IO.File]::WriteAllLines($envPath,$lines,[Text.UTF8Encoding]::new($false))
  @{database=$Database;port=3306;tableCounts=$counts;backup=$backup;migratedAt=[DateTime]::UtcNow.ToString('o')}|ConvertTo-Json -Depth 5|Set-Content -LiteralPath (Join-Path $projectRoot '.runtime/xampp-migration.json')
  Write-Output "Đã sao chép $($tables.Count) bảng sang XAMPP/$Database, kiểm tra số dòng từng bảng khớp. Nguồn cổng 3307 được giữ nguyên. Backup: $backup"
}finally{Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue;$sourcePassword=$null;$dbPassword=$null}

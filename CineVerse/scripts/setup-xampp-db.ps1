param([string]$XamppRoot='D:/xampp',[switch]$PromptRootPassword)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
& (Join-Path $PSScriptRoot 'setup-env.ps1')
. (Join-Path $PSScriptRoot 'import-env.ps1')
if(-not $PSBoundParameters.ContainsKey('XamppRoot') -and $env:XAMPP_ROOT){$XamppRoot=$env:XAMPP_ROOT}
if($env:DB_PROVIDER -ne 'xampp'){throw 'Chỉ dùng script này khi DB_PROVIDER=xampp.'}
if($env:DB_USER -notmatch '^[a-zA-Z][a-zA-Z0-9_]{0,40}$' -or -not $env:DB_PASSWORD){throw 'DB_USER/DB_PASSWORD chưa hợp lệ trong .env.'}
& (Join-Path $PSScriptRoot 'start-xampp-mysql.ps1') -XamppRoot $XamppRoot
$client=Join-Path $XamppRoot 'mysql/bin/mysql.exe'
$previousPassword=$env:MYSQL_PWD;$secure=$null
try{
  if($PromptRootPassword){$secure=Read-Host 'Mật khẩu root XAMPP (che ký tự)' -AsSecureString;$env:MYSQL_PWD=[Net.NetworkCredential]::new('',$secure).Password}else{Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue}
  $clientArgs=@('--no-defaults','--default-character-set=utf8mb4','-h','127.0.0.1','-P','3306','-u','root','--batch','--skip-column-names')
  $existing=& $client @clientArgs -e "select count(*) from information_schema.schemata where schema_name='cineverse'"
  if($LASTEXITCODE -ne 0){throw 'Không kết nối được root XAMPP. Dùng -PromptRootPassword nếu root có mật khẩu.'}
  if([long]$existing -ne 0){throw 'Database cineverse đã tồn tại; không ghi đè. Máy hiện tại đã được chuyển dữ liệu, chỉ cần start-local.ps1.'}
  $userExists=& $client @clientArgs -e "select count(*) from mysql.user where user='$env:DB_USER'"
  if($LASTEXITCODE -ne 0 -or [long]$userExists -ne 0){throw 'Không thể tạo tài khoản mới hoặc tên DB_USER đã tồn tại; không đặt lại quyền/mật khẩu tài khoản có sẵn.'}
  $escapedPassword=$env:DB_PASSWORD.Replace('\','\\').Replace("'","''")
  $sql="CREATE DATABASE cineverse CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci; CREATE USER '$env:DB_USER'@'127.0.0.1' IDENTIFIED BY '$escapedPassword'; CREATE USER '$env:DB_USER'@'localhost' IDENTIFIED BY '$escapedPassword'; GRANT ALL PRIVILEGES ON cineverse.* TO '$env:DB_USER'@'127.0.0.1'; GRANT ALL PRIVILEGES ON cineverse.* TO '$env:DB_USER'@'localhost';"
  $sql | & $client @clientArgs
  if($LASTEXITCODE -ne 0){throw 'Khởi tạo chưa hoàn tất; kiểm tra lỗi MariaDB. Không chạy lại với database có dữ liệu.'}
  Write-Output 'Đã tạo database cineverse và tài khoản chỉ có quyền trên cineverse.*. Flyway sẽ tạo bảng khi backend chạy.'
}finally{$env:MYSQL_PWD=$previousPassword;if($secure){$secure.Dispose()};$escapedPassword=$null;$sql=$null}

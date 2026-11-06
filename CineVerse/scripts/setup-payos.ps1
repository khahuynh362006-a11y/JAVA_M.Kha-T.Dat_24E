param([switch]$CheckOnly)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
. (Join-Path $PSScriptRoot 'payos-config.ps1')
if($CheckOnly){. (Join-Path $PSScriptRoot 'import-env.ps1');[ordered]@{mode=$env:PAYMENT_MODE;enabled=($env:PAYOS_ENABLED -eq 'true');clientIdPresent=([bool]$env:PAYOS_CLIENT_ID);apiKeyPresent=([bool]$env:PAYOS_API_KEY);checksumKeyPresent=([bool]$env:PAYOS_CHECKSUM_KEY)}|ConvertTo-Json;return}
Write-Output 'payOS dùng tiền ngân hàng thật, không có Sandbox tiền ảo.'
Write-Output 'Lấy ba khóa tại kênh thanh toán trong https://my.payos.vn ; không gửi khóa vào chat.'
if((Read-Host 'Gõ BAT PAYOS TIEN THAT để kích hoạt kết nối').Trim() -cne 'BAT PAYOS TIEN THAT'){throw 'Chưa kích hoạt payOS.'}
$secureValues=@();$values=@()
try{
  foreach($label in @('Client ID','API Key','Checksum Key')){$secure=Read-Host "$label (che ký tự)" -AsSecureString;$secureValues+=,$secure;$values+=([Net.NetworkCredential]::new('',$secure).Password.Trim())}
  Set-PayosConfig -EnvPath (Join-Path $projectRoot '.env') -ClientId $values[0] -ApiKey $values[1] -ChecksumKey $values[2]
}finally{$values=$null;foreach($secure in $secureValues){$secure.Dispose()}}
& (Join-Path $PSScriptRoot 'restart-backend.ps1')
$config=Invoke-RestMethod 'http://localhost:8080/api/v1/payment-config' -TimeoutSec 5
if($config.provider -ne 'PAYOS' -or -not $config.ready){throw 'Backend chưa nhận đủ cấu hình payOS.'}
Write-Output 'Đã nạp cấu hình payOS. Chưa tạo giao dịch hoặc chuyển tiền. Cần kiểm thử riêng với ngân hàng để xác minh kết nối.'

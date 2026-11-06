param([switch]$CheckOnly)
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'momo-config.ps1')
$projectRoot=Split-Path $PSScriptRoot -Parent
if($CheckOnly){
  . (Join-Path $PSScriptRoot 'import-env.ps1')
  [ordered]@{mode=$env:PAYMENT_MODE;partnerPresent=([bool]$env:MOMO_PARTNER_CODE);accessPresent=([bool]$env:MOMO_ACCESS_KEY);secretPresent=([bool]$env:MOMO_SECRET_KEY);ipnPresent=([bool]$env:MOMO_IPN_URL)}|ConvertTo-Json
  return
}
Write-Output 'CineVerse — MoMo SANDBOX. Chỉ nhập bộ khóa TEST do MoMo cấp cho bạn.'
Write-Output 'Lấy khóa: MoMo cho Doanh nghiệp → Tích hợp thanh toán → môi trường Test.'
Write-Output 'Script chỉ lưu cấu hình và restart backend, không tạo giao dịch hoặc thu tiền.'
$partner=Read-Host 'Partner Code'
$access=Read-Host 'Access Key (che ký tự)' -AsSecureString
$secret=Read-Host 'Secret Key (che ký tự)' -AsSecureString
$ipn=Read-Host 'IPN URL HTTPS công khai, kết thúc bằng /api/v1/payments/momo/ipn'
$redirect=Read-Host 'Redirect URL (Enter để dùng http://localhost:5173/thanh-toan/ket-qua)'
if([string]::IsNullOrWhiteSpace($redirect)){$redirect='http://localhost:5173/thanh-toan/ket-qua'}
try{
  $accessText=[Net.NetworkCredential]::new('',$access).Password
  $secretText=[Net.NetworkCredential]::new('',$secret).Password
  Set-MomoSandboxConfig -EnvPath (Join-Path $projectRoot '.env') -PartnerCode $partner.Trim() -AccessKey $accessText.Trim() -SecretKey $secretText.Trim() -IpnUrl $ipn.Trim() -RedirectUrl $redirect.Trim()
}finally{$accessText=$null;$secretText=$null;$access.Dispose();$secret.Dispose()}
& (Join-Path $PSScriptRoot 'restart-backend.ps1')
$config=Invoke-RestMethod 'http://localhost:8080/api/v1/payment-config' -TimeoutSec 5
if($config.provider -ne 'MOMO_SANDBOX' -or -not $config.ready){throw 'Backend chưa nhận đủ cấu hình MoMo. Xem docs/MOMO_SETUP.md.'}
Write-Output 'Đã bật cấu hình MoMo Sandbox. Cần giao dịch TEST + IPN hợp lệ để xác minh kết nối thực tế.'

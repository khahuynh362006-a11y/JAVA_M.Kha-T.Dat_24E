param([switch]$CheckOnly)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
. (Join-Path $PSScriptRoot 'vnpay-config.ps1')
if($CheckOnly){. (Join-Path $PSScriptRoot 'import-env.ps1');[ordered]@{mode=$env:PAYMENT_MODE;terminalPresent=([bool]$env:VNPAY_TMN_CODE);secretPresent=([bool]$env:VNPAY_HASH_SECRET)}|ConvertTo-Json;return}
Write-Output 'VNPAY Sandbox — dùng TmnCode và HashSecret TEST được cấp riêng cho bạn.'
Write-Output 'Đăng ký: https://sandbox.vnpayment.vn/devreg/ . Script không tạo hoặc thanh toán giao dịch.'
$terminal=(Read-Host 'vnp_TmnCode trong email VNPAY').Trim()
$secure=Read-Host 'vnp_HashSecret (che ký tự, không gửi vào chat)' -AsSecureString
try{$secret=[Net.NetworkCredential]::new('',$secure).Password;Set-VnpaySandboxConfig -EnvPath (Join-Path $projectRoot '.env') -TmnCode $terminal -HashSecret $secret.Trim()}
finally{$secret=$null;$secure.Dispose()}
& (Join-Path $PSScriptRoot 'restart-backend.ps1')
$config=Invoke-RestMethod 'http://localhost:8080/api/v1/payment-config' -TimeoutSec 5
if($config.provider -ne 'VNPAY_SANDBOX' -or -not $config.ready){throw 'Backend chưa nhận đủ cấu hình VNPAY.'}
Write-Output 'Đã lưu cấu hình. Đặt đơn vé mới để sang trang VNPAY Sandbox và thanh toán bằng thẻ Test.'
Write-Output 'Đủ cấu hình chưa có nghĩa là VNPAY đã chấp nhận merchant; cần thực hiện giao dịch Test để xác minh.'

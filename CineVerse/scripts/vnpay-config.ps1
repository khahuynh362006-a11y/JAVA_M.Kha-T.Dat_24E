function Set-VnpaySandboxConfig {
  param([Parameter(Mandatory)][string]$EnvPath,[Parameter(Mandatory)][string]$TmnCode,[Parameter(Mandatory)][string]$HashSecret,[string]$ReturnUrl='http://localhost:8080/api/v1/payments/vnpay/return')
  if($TmnCode -notmatch '^[a-zA-Z0-9]{1,32}$' -or $HashSecret -notmatch '^[\x21-\x7e]{16,512}$'){throw 'TmnCode/HashSecret không hợp lệ. Nhập bộ thông tin Sandbox VNPAY gửi cho bạn.'}
  $uri=[uri]$ReturnUrl
  if(-not $uri.IsAbsoluteUri -or $uri.UserInfo -or $uri.Query -or $uri.Fragment -or $uri.AbsolutePath -ne '/api/v1/payments/vnpay/return' -or ($uri.Scheme -ne 'https' -and -not($uri.IsLoopback -and $uri.Scheme -eq 'http'))){throw 'Return URL phải dùng HTTPS hoặc localhost, kết thúc bằng /api/v1/payments/vnpay/return.'}
  $settings=[ordered]@{PAYMENT_MODE='vnpay';VNPAY_TMN_CODE=$TmnCode;VNPAY_HASH_SECRET=$HashSecret;VNPAY_RETURN_URL=$ReturnUrl;VNPAY_CLIENT_IP='127.0.0.1'}
  $lines=[Collections.Generic.List[string]]::new()
  foreach($line in [IO.File]::ReadAllLines($EnvPath)){if($line -match '^([A-Z][A-Z0-9_]*)=' -and $settings.Contains($matches[1])){continue};$lines.Add($line)}
  foreach($key in $settings.Keys){$lines.Add("$key=$($settings[$key])")}
  $temporary="$EnvPath.vnpay-tmp"
  try{[IO.File]::WriteAllLines($temporary,$lines,[Text.UTF8Encoding]::new($false));[IO.File]::Move($temporary,$EnvPath,$true)}finally{if(Test-Path -LiteralPath $temporary){Remove-Item -LiteralPath $temporary}}
}

function Set-MomoSandboxConfig {
  param([Parameter(Mandatory)][string]$EnvPath,[Parameter(Mandatory)][string]$PartnerCode,[Parameter(Mandatory)][string]$AccessKey,[Parameter(Mandatory)][string]$SecretKey,[Parameter(Mandatory)][string]$IpnUrl,[string]$RedirectUrl='http://localhost:5173/thanh-toan/ket-qua')
  foreach($value in @($PartnerCode,$AccessKey,$SecretKey)){
    if($value -notmatch '^[\x21-\x7e]{1,512}$'){throw 'Thông tin merchant chứa khoảng trắng hoặc ký tự xuống dòng. Sao chép đúng bộ khóa Sandbox.'}
  }
  if($SecretKey.Length -lt 16){throw 'Secret Key không hợp lệ.'}
  $ipn=[uri]$IpnUrl
  if(-not $ipn.IsAbsoluteUri -or $ipn.Scheme -ne 'https' -or $ipn.IsLoopback -or $ipn.UserInfo -or $ipn.Query -or $ipn.Fragment -or $ipn.AbsolutePath -ne '/api/v1/payments/momo/ipn'){
    throw 'IPN phải là URL HTTPS công khai, kết thúc bằng /api/v1/payments/momo/ipn.'
  }
  $redirect=[uri]$RedirectUrl
  if(-not $redirect.IsAbsoluteUri -or $redirect.UserInfo -or $redirect.Fragment -or ($redirect.Scheme -ne 'https' -and -not($redirect.IsLoopback -and $redirect.Scheme -eq 'http'))){throw 'Redirect URL phải dùng HTTPS hoặc HTTP localhost.'}
  $settings=[ordered]@{PAYMENT_MODE='momo';MOMO_ENABLED='true';MOMO_MODE='sandbox';MOMO_PARTNER_CODE=$PartnerCode;MOMO_ACCESS_KEY=$AccessKey;MOMO_SECRET_KEY=$SecretKey;MOMO_IPN_URL=$IpnUrl;MOMO_REDIRECT_URL=$RedirectUrl}
  $lines=[Collections.Generic.List[string]]::new()
  foreach($line in [IO.File]::ReadAllLines($EnvPath)){
    if($line -match '^([A-Z][A-Z0-9_]*)=' -and $settings.Contains($matches[1])){continue}
    $lines.Add($line)
  }
  foreach($key in $settings.Keys){$lines.Add("$key=$($settings[$key])")}
  $temporary="$EnvPath.momo-tmp"
  try{[IO.File]::WriteAllLines($temporary,$lines,[Text.UTF8Encoding]::new($false));[IO.File]::Move($temporary,$EnvPath,$true)}
  finally{if(Test-Path -LiteralPath $temporary){Remove-Item -LiteralPath $temporary}}
}

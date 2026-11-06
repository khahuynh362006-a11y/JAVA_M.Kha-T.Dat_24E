function Set-GmailConfig {
  param([Parameter(Mandatory)][string]$EnvPath,[Parameter(Mandatory)][string]$Email,[Parameter(Mandatory)][string]$AppPassword)
  $address=[System.Net.Mail.MailAddress]::new($Email.Trim())
  if($address.Address -ne $Email.Trim()){throw 'Nhập địa chỉ email, không kèm tên hiển thị.'}
  $secret=$AppPassword -replace '\s',''
  if($secret -notmatch '^[a-zA-Z0-9]{16}$'){throw 'Mật khẩu ứng dụng Google cần đúng 16 ký tự (bỏ khoảng trắng).'}
  $settings=[ordered]@{MAIL_HOST='smtp.gmail.com';MAIL_PORT='587';MAIL_USERNAME=$address.Address;MAIL_PASSWORD=$secret;MAIL_AUTH='true';MAIL_TLS='true';MAIL_FROM=$address.Address}
  $lines=[Collections.Generic.List[string]]::new()
  foreach($line in [IO.File]::ReadAllLines($EnvPath)){
    if($line -match '^([A-Z][A-Z0-9_]*)=' -and $settings.Contains($matches[1])){continue}
    $lines.Add($line)
  }
  foreach($key in $settings.Keys){$lines.Add("$key=$($settings[$key])")}
  # Same-directory atomic replacement; never write secrets into logs or backups.
  $temporary="$EnvPath.gmail-tmp"
  try{
    [IO.File]::WriteAllLines($temporary,$lines,[Text.UTF8Encoding]::new($false))
    [IO.File]::Move($temporary,$EnvPath,$true)
  }finally{if(Test-Path -LiteralPath $temporary){Remove-Item -LiteralPath $temporary}}
}

function Set-BrevoConfig {
  param([Parameter(Mandatory)][string]$EnvPath,[Parameter(Mandatory)][string]$Email,[Parameter(Mandatory)][string]$Login,[Parameter(Mandatory)][string]$SmtpKey)
  foreach($value in @($Email,$Login)){
    $address=[Net.Mail.MailAddress]::new($value)
    if($address.Address -ne $value){throw 'Invalid email address'}
  }
  if($SmtpKey -notmatch '^[\x21-\x7e]{10,512}$'){throw 'Invalid SMTP key'}
  $settings=[ordered]@{MAIL_HOST='smtp-relay.brevo.com';MAIL_PORT='587';MAIL_USERNAME=$Login;MAIL_PASSWORD=$SmtpKey;MAIL_AUTH='true';MAIL_TLS='true';MAIL_FROM=$Email}
  $lines=[Collections.Generic.List[string]]::new()
  foreach($line in [IO.File]::ReadAllLines($EnvPath)){
    if($line -match '^([A-Z][A-Z0-9_]*)=' -and $settings.Contains($matches[1])){continue}
    $lines.Add($line)
  }
  foreach($key in $settings.Keys){$lines.Add("$key=$($settings[$key])")}
  $temporary="$EnvPath.smtp-tmp"
  try{
    [IO.File]::WriteAllLines($temporary,$lines,[Text.UTF8Encoding]::new($false))
    [IO.File]::Move($temporary,$EnvPath,$true)
  }finally{if(Test-Path -LiteralPath $temporary){Remove-Item -LiteralPath $temporary}}
}

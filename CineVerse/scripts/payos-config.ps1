function Set-PayosConfig {
  param([Parameter(Mandatory)][string]$EnvPath,[Parameter(Mandatory)][string]$ClientId,[Parameter(Mandatory)][string]$ApiKey,[Parameter(Mandatory)][string]$ChecksumKey)
  foreach($value in @($ClientId,$ApiKey,$ChecksumKey)){
    if($value -notmatch '^[\x21-\x7e]{16,512}$'){throw 'Khóa payOS không hợp lệ; lấy đúng ba khóa của kênh thanh toán của bạn.'}
  }
  $settings=[ordered]@{PAYMENT_MODE='payos';PAYOS_ENABLED='true';PAYOS_CLIENT_ID=$ClientId;PAYOS_API_KEY=$ApiKey;PAYOS_CHECKSUM_KEY=$ChecksumKey}
  $lines=[Collections.Generic.List[string]]::new()
  foreach($line in [IO.File]::ReadAllLines($EnvPath)){if($line -match '^([A-Z][A-Z0-9_]*)=' -and $settings.Contains($matches[1])){continue};$lines.Add($line)}
  foreach($key in $settings.Keys){$lines.Add("$key=$($settings[$key])")}
  $temporary="$EnvPath.payos-tmp"
  try{[IO.File]::WriteAllLines($temporary,$lines,[Text.UTF8Encoding]::new($false));[IO.File]::Move($temporary,$EnvPath,$true)}finally{if(Test-Path -LiteralPath $temporary){Remove-Item -LiteralPath $temporary}}
}

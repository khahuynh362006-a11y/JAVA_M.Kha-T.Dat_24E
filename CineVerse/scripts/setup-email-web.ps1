param([int]$Port=8765,[switch]$CheckOnly)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
. (Join-Path $PSScriptRoot 'gmail-config.ps1')
if($CheckOnly){Write-Output 'Email setup server parsed; no network calls or changes.';return}
$token=[Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(24))
$origin="http://localhost:$Port"
$url="$origin/$token/"
$listener=[Net.HttpListener]::new();$listener.Prefixes.Add("$origin/");$listener.Start()
[IO.File]::WriteAllText((Join-Path $projectRoot '.runtime/email-setup-url.txt'),$url)
Write-Output ('Mo trang nay de nhap thong tin SMTP: '+$url)
$page=@'
<!doctype html><html lang="vi"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>CineVerse — Gửi email đến Gmail</title>
<style>*{box-sizing:border-box}body{background:#090c12;color:#f3f5fa;font:16px/1.6 system-ui;margin:0;padding:40px 20px}main{max-width:660px;margin:auto}small{color:#9ba9bf}h1{line-height:1.2}a{color:#91bdff}section,form{background:#121925;border:1px solid #293449;border-radius:16px;padding:24px;margin:22px 0}label{display:block;margin:16px 0 6px}input{width:100%;padding:13px;background:#080e18;border:1px solid #49546a;border-radius:8px;color:white;font:inherit}button{padding:14px 22px;background:#e9c079;color:#13151b;border:0;border-radius:8px;font:600 16px system-ui;cursor:pointer}.status{background:#213727;padding:16px;border-radius:10px}</style>
<main><small>CINEVERSE · THIẾT LẬP MỘT LẦN</small><h1>Tự động gửi thư tới Gmail.</h1><p>Dùng Brevo làm dịch vụ gửi. Không cần mật khẩu đăng nhập hoặc mật khẩu ứng dụng Google.</p>
<section><b>Bước 1 · Lấy thông tin từ Brevo</b><ol><li><a href="https://app.brevo.com/account/register" target="_blank" rel="noreferrer">Tạo tài khoản Brevo</a> và xác nhận email.</li><li>Thêm địa chỉ gửi trong <a href="https://app.brevo.com/senders/list" target="_blank" rel="noreferrer">Senders</a>, xác minh địa chỉ đó qua thư Brevo.</li><li>Mở <a href="https://app.brevo.com/settings/keys/smtp" target="_blank" rel="noreferrer">SMTP &amp; API → SMTP</a>, sao chép <b>Login</b> và tạo <b>SMTP key</b>. Không dùng API key.</li></ol><small>Brevo có thể yêu cầu duyệt tài khoản/quyền gửi. Với địa chỉ gửi @gmail.com, Brevo có thể đổi miền người gửi sang brevosend.com. Tên miền riêng được xác thực phù hợp hơn khi vận hành thật.</small></section>
<form method="post" autocomplete="off"><b>Bước 2 · Kiểm tra và kích hoạt</b><label for="email">Địa chỉ gửi đã xác minh trong Brevo</label><input id="email" name="email" type="email" required placeholder="ban@gmail.com"><small>Thư thử chỉ gửi tới chính địa chỉ này.</small><label for="login">SMTP Login — sao chép đúng từ Brevo</label><input id="login" name="login" type="email" required placeholder="…@smtp-brevo.com"><label for="key">SMTP key</label><input id="key" name="key" type="password" required minlength="10" maxlength="512"><p><small>Khóa được lưu trong .env trên máy sau khi gửi thử thành công. Không gửi khóa vào chat.</small></p><button type="submit">Gửi thư thử và kích hoạt</button><p>Thao tác sẽ gửi 1 thư thử, lưu cấu hình và khởi động lại backend. Có thể mất khoảng 30 giây.</p></form>
<section><b>Bước 3 · Email sau khi mua vé</b><p>Sau khi kích hoạt, khách thanh toán thành công sẽ tự nhận thông tin phim, suất chiếu, ghế, số tiền và liên kết vé tại email tài khoản. Kiểm tra cả thư mục Spam.</p></section></main></html>
'@
try{
  while($listener.IsListening){
    $context=$listener.GetContext();$request=$context.Request;$response=$context.Response
    $response.Headers.Add('Cache-Control','no-store');$response.Headers.Add('X-Content-Type-Options','nosniff');$response.Headers.Add('X-Frame-Options','DENY')
    $response.Headers.Add('Referrer-Policy','no-referrer');$response.Headers.Add('Content-Security-Policy',"default-src 'none'; style-src 'unsafe-inline'; form-action 'self'; frame-ancestors 'none'; base-uri 'none'")
    $body=$page
    if(-not [Net.IPAddress]::IsLoopback($request.RemoteEndPoint.Address) -or $request.Url.AbsolutePath -ne "/$token/" -or $request.Url.Authority -ne "localhost:$Port"){$response.StatusCode=404;$body='Not found'}
    elseif($request.HttpMethod -eq 'POST'){
      $saved=$false;$smtp=$null;$message=$null;$secret=$null
      try{
        if($request.Headers['Origin'] -ne $origin -or $request.ContentLength64 -gt 4096 -or $request.ContentLength64 -lt 1){throw 'Invalid request'}
        $reader=[IO.StreamReader]::new($request.InputStream);$raw=$reader.ReadToEnd();$reader.Dispose();$values=@{}
        foreach($item in $raw.Split('&')){$pair=$item.Split('=',2);if($pair.Count -eq 2){$values[[Net.WebUtility]::UrlDecode($pair[0])]=[Net.WebUtility]::UrlDecode($pair[1])}}
        $email=([string]$values['email']).Trim();$login=([string]$values['login']).Trim();$secret=([string]$values['key']).Trim()
        foreach($address in @($email,$login)){if([Net.Mail.MailAddress]::new($address).Address -ne $address){throw 'Invalid address'}}
        if($secret -notmatch '^[\x21-\x7e]{10,512}$'){throw 'Invalid key'}
        $smtp=[Net.Mail.SmtpClient]::new('smtp-relay.brevo.com',587);$smtp.EnableSsl=$true;$smtp.Timeout=20000;$smtp.UseDefaultCredentials=$false;$smtp.Credentials=[Net.NetworkCredential]::new($login,$secret)
        $message=[Net.Mail.MailMessage]::new($email,$email);$message.Subject='CineVerse | Kiểm tra gửi email';$message.Body='CineVerse đã kết nối dịch vụ gửi thư. Khi thanh toán vé thành công, khách sẽ tự nhận thông tin đơn tại email tài khoản.';$message.SubjectEncoding=[Text.Encoding]::UTF8;$message.BodyEncoding=[Text.Encoding]::UTF8
        $smtp.Send($message)
        Set-BrevoConfig -EnvPath (Join-Path $projectRoot '.env') -Email $email -Login $login -SmtpKey $secret
        $saved=$true
        & (Join-Path $PSScriptRoot 'restart-backend.ps1') | Out-Null
        @{status='CONFIGURED';provider='BREVO';updatedAt=[DateTime]::UtcNow.ToString('o')}|ConvertTo-Json|Set-Content -LiteralPath (Join-Path $projectRoot '.runtime/email-setup-status.json')
        $body='<meta charset="utf-8"><h1>Đã kích hoạt gửi email tự động.</h1><p>Dịch vụ gửi đã nhận thư thử. Kiểm tra Gmail và Spam.</p><p><a href="http://localhost:5173/dang-nhap">Về CineVerse</a> để mua vé và nhận thông tin qua email.</p>'
      }catch{
        $notice=if($saved){'Đã lưu cấu hình nhưng backend chưa healthy. Chạy scripts/restart-backend.ps1.'}else{'Chưa thay đổi cấu hình. Kiểm tra SMTP Login, SMTP key, địa chỉ gửi đã xác minh và quyền gửi của tài khoản Brevo. Không nhập mật khẩu Google.'}
        $body=$page.Replace('<h1>Tự động gửi thư tới Gmail.</h1>',"<h1>Chưa hoàn tất.</h1><p class='status'>$notice</p>");$response.StatusCode=400
      }finally{$secret=$null;$values=$null;$raw=$null;if($message){$message.Dispose()};if($smtp){$smtp.Dispose()}}
    }elseif($request.HttpMethod -ne 'GET'){$response.StatusCode=405;$body='Method not allowed'}
    $bytes=[Text.Encoding]::UTF8.GetBytes($body);$response.ContentType='text/html; charset=utf-8';$response.ContentLength64=$bytes.Length
    try{$response.OutputStream.Write($bytes,0,$bytes.Length)}finally{$response.Close()}
  }
}finally{$listener.Stop();$listener.Close()}

param([switch]$CheckOnly,[switch]$CheckUi)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
. (Join-Path $PSScriptRoot 'gmail-config.ps1')
if($CheckOnly){Write-Output 'Gmail setup script loaded (no credentials used, no email sent).';return}
Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing
[Windows.Forms.Application]::EnableVisualStyles()
$form=[Windows.Forms.Form]::new()
$form.Text='CineVerse — Kết nối Gmail tự động'
$form.ClientSize=[Drawing.Size]::new(620,470)
$form.StartPosition='CenterScreen'
$form.FormBorderStyle='FixedDialog'
$form.MaximizeBox=$false
$form.Font=[Drawing.Font]::new('Segoe UI',10)
function LabelAt($Text,$Y,$Height=30){
  $label=[Windows.Forms.Label]::new();$label.Text=$Text;$label.Location=[Drawing.Point]::new(24,$Y);$label.Size=[Drawing.Size]::new(570,$Height);$form.Controls.Add($label)
}
LabelAt 'Thiết lập một lần — sau đó website tự gửi email xác thực.' 20
LabelAt '1. Bật xác minh 2 bước trong tài khoản Google.' 54
$link=[Windows.Forms.LinkLabel]::new();$link.Text='2. Mở Google để tạo Mật khẩu ứng dụng: CineVerse';$link.Location=[Drawing.Point]::new(24,87);$link.Size=[Drawing.Size]::new(570,30)
$link.Add_LinkClicked({Start-Process 'https://myaccount.google.com/apppasswords'})
$form.Controls.Add($link)
LabelAt 'Gmail gửi thư (có thể dùng chính Gmail bạn đã đăng ký)' 125
$email=[Windows.Forms.TextBox]::new();$email.Location=[Drawing.Point]::new(24,155);$email.Width=570;$form.Controls.Add($email)
LabelAt 'Mật khẩu ứng dụng 16 ký tự — không dùng mật khẩu đăng nhập' 194
$password=[Windows.Forms.TextBox]::new();$password.Location=[Drawing.Point]::new(24,224);$password.Width=570;$password.UseSystemPasswordChar=$true;$form.Controls.Add($password)
LabelAt 'Nút bên dưới gửi 1 thư thử tới chính Gmail này, lưu SMTP vào .env trên máy rồi khởi động lại backend. Không gửi mật khẩu vào chat.' 264 55
$button=[Windows.Forms.Button]::new();$button.Text='Gửi thư thử và kích hoạt';$button.Location=[Drawing.Point]::new(24,330);$button.Size=[Drawing.Size]::new(300,42);$form.Controls.Add($button)
$status=[Windows.Forms.Label]::new();$status.Location=[Drawing.Point]::new(24,385);$status.Size=[Drawing.Size]::new(570,70);$form.Controls.Add($status)
$button.Add_Click({
  $button.Enabled=$false;$email.Enabled=$false;$password.Enabled=$false
  $smtp=$null;$message=$null;$saved=$false
  try{
    $address=[Net.Mail.MailAddress]::new($email.Text.Trim())
    if($address.Address -ne $email.Text.Trim()){throw 'Invalid email'}
    $secret=$password.Text -replace '\s',''
    if($secret -notmatch '^[a-zA-Z0-9]{16}$'){throw 'Invalid app password'}
    $envPath=Join-Path $projectRoot '.env'
    if(-not(Test-Path -LiteralPath $envPath)){throw 'Run setup-env first'}
    $status.Text='Đang gửi thư thử qua Gmail SMTP (STARTTLS)...';[Windows.Forms.Application]::DoEvents()
    $smtp=[Net.Mail.SmtpClient]::new('smtp.gmail.com',587);$smtp.EnableSsl=$true;$smtp.Timeout=20000;$smtp.UseDefaultCredentials=$false
    $smtp.Credentials=[Net.NetworkCredential]::new($address.Address,$secret)
    $message=[Net.Mail.MailMessage]::new($address.Address,$address.Address)
    $message.Subject='CineVerse | Kiểm tra kết nối Gmail'
    $message.Body='Gmail SMTP đã nhận thư thử từ CineVerse. Sau khi backend khởi động lại, hãy đăng ký hoặc bấm Gửi lại email xác thực trên website. Thư này không phải email xác thực tài khoản.'
    $message.SubjectEncoding=[Text.Encoding]::UTF8;$message.BodyEncoding=[Text.Encoding]::UTF8
    $smtp.Send($message)
    Set-GmailConfig -EnvPath $envPath -Email $address.Address -AppPassword $secret
    $saved=$true;$password.Clear();$secret=$null
    $status.Text='Gmail đã nhận thư thử. Đang khởi động lại backend...';[Windows.Forms.Application]::DoEvents()
    & (Join-Path $PSScriptRoot 'restart-backend.ps1') | Out-Null
    $status.Text='Đã kích hoạt Gmail. Kiểm tra Hộp thư đến/Spam và bấm Gửi lại email xác thực trên website.'
    $button.Text='Đã kích hoạt';$link.Enabled=$false
    @{status='CONFIGURED';updatedAt=[DateTime]::UtcNow.ToString('o')}|ConvertTo-Json|Set-Content -LiteralPath (Join-Path $projectRoot '.runtime/gmail-setup-status.json')
  }catch{
    $status.Text=if($saved){'Đã lưu SMTP nhưng backend chưa khởi động được. Chạy scripts/restart-backend.ps1 rồi thử đăng ký lại.'}else{'Chưa lưu cấu hình. Kiểm tra Gmail, mật khẩu ứng dụng 16 ký tự và mạng. Bật xác minh 2 bước; không dùng mật khẩu đăng nhập Google.'}
    $button.Enabled=$true;$email.Enabled=$true;$password.Enabled=$true
  }finally{
    $secret=$null
    if($message){$message.Dispose()};if($smtp){$smtp.Dispose()}
  }
})
$form.AcceptButton=$button
if($CheckUi){Write-Output 'Gmail setup UI constructed successfully (not displayed, no email sent).'}else{[void]$form.ShowDialog()}
$password.Clear();$form.Dispose()

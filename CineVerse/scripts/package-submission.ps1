$ErrorActionPreference='Stop'
$projectRoot=(Resolve-Path -LiteralPath (Split-Path $PSScriptRoot -Parent)).Path
$submissionRoot=Join-Path $projectRoot 'NopBai'
$label='CineVerse-NopBai-'+(Get-Date -Format 'yyyyMMdd-HHmmss')
$destination=Join-Path $submissionRoot $label
New-Item -ItemType Directory -Path $destination -Force | Out-Null
$excluded=@('node_modules','target','dist','test-results','playwright-report','.git','.runtime','.tools','.idea','.vscode')
function Copy-SubmissionDirectory([string]$Source,[string]$Target){
  New-Item -ItemType Directory -Path $Target -Force | Out-Null
  foreach($item in Get-ChildItem -LiteralPath $Source -Force){
    if($item.Name -in $excluded){continue}
    if($item.PSIsContainer){Copy-SubmissionDirectory $item.FullName (Join-Path $Target $item.Name);continue}
    if($item.Name -like '.env*' -and $item.Name -ne '.env.example'){continue}
    if($item.Extension -in @('.log','.pid','.tsbuildinfo') -or $item.Name -eq 'bunny-full.mp4'){continue}
    if($item.FullName -like '*\docs\screenshots\*' -and $item.Name -notlike 'poster-collection-*'){continue}
    Copy-Item -LiteralPath $item.FullName -Destination (Join-Path $Target $item.Name)
  }
}
foreach($folder in @('frontend','admin','backend','scripts','docs','demo-media')){Copy-SubmissionDirectory (Join-Path $projectRoot $folder) (Join-Path $destination $folder)}
foreach($file in @('README.md','TASKS.md','AGENTS.md','.env.example','.gitignore','.dockerignore','.prettierrc.json','docker-compose.yml','Cai-dat-payOS.cmd')){Copy-Item -LiteralPath (Join-Path $projectRoot $file) -Destination (Join-Path $destination $file)}
$example=Join-Path $destination '.env.example'
[IO.File]::WriteAllText($example,[IO.File]::ReadAllText($example).Replace('XAMPP_ROOT=D:/xampp','XAMPP_ROOT=C:/xampp'))
$guide=@'
# CINEVERSE — HƯỚNG DẪN BẢN NỘP

Đồ án Java full-stack bán vé xem phim. Hai ứng dụng: khách 5173, admin 5174; Java API 8080; MariaDB/MySQL XAMPP 3306.

## Chuẩn bị trên máy giảng viên

- JDK 21, Node.js 22.12+ hoặc 24, PowerShell 7, XAMPP và Mailpit (SMTP thử local).
- Có Internet lần đầu để tải thư viện Maven/npm. Không cần thư mục node_modules hoặc target từ máy sinh viên.
- Giải nén vào một thư mục có quyền ghi; mở PowerShell 7 tại thư mục chứa README.md.

## Chạy bằng XAMPP

1. Chạy: pwsh -File scripts/setup-env.ps1
2. Mở .env vừa tạo, đặt XAMPP_ROOT đúng thư mục XAMPP trên máy (mặc định bản nộp C:/xampp). Không chia sẻ .env.
3. Với XAMPP chưa có database cineverse, chạy: pwsh -File scripts/setup-xampp-db.ps1 -XamppRoot 'C:/xampp'
   Thêm -PromptRootPassword nếu root có mật khẩu. Script từ chối ghi đè database/tài khoản có sẵn.
4. Cài Mailpit, rồi chạy: pwsh -File scripts/start-local.ps1 -MailpitExe 'C:/tools/mailpit.exe'
   Đổi đường dẫn theo vị trí Mailpit thực tế. Script chạy kiểm thử/build backend và hai frontend, rồi khởi động dịch vụ.
5. Xem tài khoản/mật khẩu demo mới của máy: pwsh -File scripts/show-demo.ps1

Khách: http://localhost:5173 — Admin: http://localhost:5174 — Mailpit: http://localhost:8025.
Tài khoản admin: admin@cineverse.local. Mật khẩu được sinh mới khi setup, không dùng mật khẩu của máy sinh viên.
Dừng các tiến trình dự án bằng: pwsh -File scripts/stop-local.ps1. XAMPP dùng chung không bị dừng/xóa.

## Cơ sở dữ liệu và dữ liệu mẫu

SQL gốc: backend/src/main/resources/db/migration/V1...V5.sql.
Flyway tự tạo bảng và lưu lịch sử khi backend chạy trên database trống; Java seed tạo phim, rạp, ghế, suất và tài khoản demo. Catalog tại backend/src/main/resources/seed/catalog.json.
Không nhập thủ công các migration rồi chạy Flyway lần nữa. Không kèm bản sao CSDL cá nhân của máy sinh viên, tài khoản khách, giao dịch, token hoặc bảng quyền XAMPP.
Ảnh 40 phim và trailer minh họa nằm trong source; nguồn/giấy phép tại docs/MEDIA.md và docs/ASSETS.md.

## Thanh toán và email

Bản nộp dùng payOS nhưng tắt kết nối mặc định (PAYOS_ENABLED=false), không kèm khóa riêng. Muốn chạy thanh toán thật, chủ tài khoản nhập ba khóa của chính mình bằng scripts/setup-payos.ps1. payOS không có Sandbox; thao tác chuyển tiền là tiền ngân hàng thật. Không dùng khóa của sinh viên.
Email Internet chưa cấu hình; Mailpit nhận thư thử tại máy local. Không cần Brevo/Gmail để đăng ký hoặc xem vé. Không có bước bắt buộc xác thực email khi đăng ký.

## Tài liệu và kiểm thử

Báo cáo: docs/BAO_CAO_DO_AN.md. Kiến trúc, ERD, UML, API, kịch bản demo và bằng chứng kiểm thử trong docs/.
Kiểm Java: pwsh -File scripts/maven.ps1 -B test
Kiểm frontend: npm.cmd --prefix frontend run build
Kiểm admin: npm.cmd --prefix admin run build
Docker Compose được cung cấp làm phương án khác, chưa chạy xác minh trên máy sinh viên.

Đây là bản mã nguồn và tài nguyên để nộp; đã loại .env, khóa, cache, node_modules, target, dist, log, uploads và dữ liệu người dùng. Một số ảnh/log kiểm thử lịch sử được tài liệu dẫn chiếu không nằm trong bản nộp để tránh đưa dữ liệu cá nhân.
'@
[IO.File]::WriteAllText((Join-Path $destination 'HUONG-DAN-NOP-BAI.md'),$guide,[Text.UTF8Encoding]::new($false))
# Check exact configured secrets without displaying their values.
$secrets=@()
foreach($line in Get-Content -LiteralPath (Join-Path $projectRoot '.env')){if($line -match '^(PAYOS_CLIENT_ID|PAYOS_API_KEY|PAYOS_CHECKSUM_KEY|DB_PASSWORD|JWT_SECRET|MAIL_PASSWORD)=(.+)$' -and $matches[2].Length -ge 12){$secrets+=,$matches[2]}}
foreach($file in Get-ChildItem -LiteralPath $destination -Recurse -File){
  if($file.Extension -in @('.md','.json','.yml','.yaml','.java','.ps1','.cmd','.html','.txt','.sql','.ts','.tsx') -or $file.Name -eq '.env.example'){
    $content=[IO.File]::ReadAllText($file.FullName)
    foreach($secret in $secrets){if($content.Contains($secret)){throw "Bản nộp chứa giá trị cấu hình riêng: $($file.FullName)"}}
  }
}
$secrets=$null
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive=Join-Path $submissionRoot ($label+'.zip')
[IO.Compression.ZipFile]::CreateFromDirectory($destination,$archive,[IO.Compression.CompressionLevel]::Optimal,$true)
$zip=[IO.Compression.ZipFile]::OpenRead($archive)
try{
  if($zip.Entries.FullName -match '(^|/)(node_modules|target|dist|\.runtime|\.git)/|(^|/)\.env$'){throw 'Archive contains excluded paths'}
  foreach($required in @('backend/pom.xml','frontend/package.json','admin/package.json','HUONG-DAN-NOP-BAI.md','frontend/public/art/poster-collection.json')){if(-not($zip.Entries.FullName -contains "$label/$required")){throw "Archive missing $required"}}
  $entryCount=$zip.Entries.Count
}finally{$zip.Dispose()}
@{folder=$destination;zip=$archive;files=$entryCount;zipBytes=(Get-Item -LiteralPath $archive).Length}|ConvertTo-Json

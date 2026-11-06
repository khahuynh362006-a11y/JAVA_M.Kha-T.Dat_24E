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
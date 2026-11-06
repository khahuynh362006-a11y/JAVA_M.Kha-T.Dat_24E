# Hai website dùng chung SQL XAMPP

Khách hàng: `http://localhost:5173`. Quản trị: `http://localhost:5174` (chuyển tới `/admin`). Backend Java: `http://localhost:8080`. Database hiện tại: MariaDB 10.4.32 của XAMPP tại `D:/xampp`, cổng `127.0.0.1:3306`, database `cineverse`. XAMPP gọi dịch vụ là MySQL; bộ cài máy này thực tế cung cấp MariaDB.

Admin và khách là hai ứng dụng React có entry point/build riêng, dùng chung dịch vụ Java và CSDL. Trình duyệt không kết nối SQL trực tiếp. Java dùng tài khoản `cineverse_app` chỉ có quyền trên `cineverse.*`; mật khẩu nằm trong `.env`, không phải tài khoản root. Không cần bật Apache để chạy hai trang React/Java. Có thể mở phpMyAdmin qua Apache của XAMPP nếu muốn xem bảng; việc nhập/sửa trực tiếp SQL bỏ qua kiểm tra nghiệp vụ nên ưu tiên trang admin.

## Chạy lại trên máy hiện tại

```powershell
pwsh -File scripts/start-local.ps1
pwsh -File scripts/show-demo.ps1
```

`start-local` đọc `DB_PROVIDER=xampp`, dùng XAMPP 3306, chạy kiểm thử/build và mở backend, customer, admin, Mailpit nền ẩn. `-SkipBuild` dùng khi bản hiện tại đã build. Mật khẩu demo được sinh riêng trên máy; tài khoản admin là `admin@cineverse.local`. `stop-local.ps1` chỉ dừng tiến trình dự án ghi nhận, không tự dừng XAMPP dùng chung hoặc xóa dữ liệu.

Cookie refresh admin là `cv_admin_refresh`, khách là `cv_refresh`, chọn theo Origin được cho phép chính xác. Hai cổng localhost vẫn dùng chung cookie domain nên cần hai tên riêng. API admin luôn kiểm role `ADMIN`; khách không có quyền đọc/sửa qua API dù biết URL. Cập nhật catalog từ admin được khách đọc từ cùng CSDL, tự tải lại khi focus hoặc sau tối đa khoảng 30 giây; form đang soạn của admin không bị polling ghi đè.

## Cài mới trên máy khác

1. Cài JDK 21, Node 22.12+ hoặc 24, PowerShell 7, XAMPP/MariaDB và Mailpit.
2. Chạy `scripts/setup-env.ps1`, sửa `XAMPP_ROOT` và đường dẫn Mailpit phù hợp. Không copy secret của máy khác.
3. Với XAMPP chưa có database `cineverse`, chạy `pwsh -File scripts/setup-xampp-db.ps1 -XamppRoot 'C:/xampp'` (thêm `-PromptRootPassword` nếu root có mật khẩu). Script từ chối database/tài khoản đã tồn tại, không ghi đè.
4. Chạy `scripts/setup-media.ps1`, `scripts/start-local.ps1 -MailpitExe 'C:/tools/mailpit.exe'`. Flyway tự tạo schema mới và seed dev. Có 5 migrations, 32 bảng gồm bảng lịch sử Flyway. Không import lại schema cũ vào database hiện tại.

## Chuyển dữ liệu và phục hồi ngày 05/11/2026

- Đã xuất 31 bảng từ nguồn MySQL 8.4 tại 3307 sang XAMPP, đối chiếu số dòng của cả 31 bảng bằng nhau trước khi chuyển `.env`. V5 sau đó thêm `payos_links`. File dữ liệu SQL không đổi; DDL chỉ đổi collation MySQL `utf8mb4_0900_ai_ci` sang `utf8mb4_unicode_ci` mà MariaDB hỗ trợ.
- Sao lưu dữ liệu và cấu hình nguồn: `.runtime/backups/xampp-20261105-204807/`. Kết quả đối chiếu: `.runtime/xampp-migration.json`. Nguồn `.runtime/mysql` được giữ nguyên.
- XAMPP có sẵn hai bảng Aria quyền bị hỏng: `mysql.db`, `mysql.tables_priv`. Bản thử repair trên bản sao không giữ đủ 3 dòng quyền, nên không dùng bản đó. Đã sao lưu thư mục hệ thống khi MariaDB dừng tại `.runtime/backups/xampp-system-20261105-205032/mysql/`.
- Theo sự cho phép rõ ràng của chủ máy, đã khôi phục đúng hai bảng từ bản mặc định `D:/xampp/mysql/backup/mysql`, kiểm `CHECK TABLE` đều OK, rồi cấp lại quyền riêng cho tài khoản CineVerse mới tạo. **Quyền tùy chỉnh cũ trong hai bảng này có thể cần cấp lại.** Dữ liệu các database khác không bị thay thế.
- Đây là thao tác phục hồi một lần, không tự chạy trong script khởi động. Không chép đè cả thư mục `mysql/data` và không xóa database dự án khác để sửa lỗi.

`migrate-to-xampp.ps1` chỉ dành cho lần chuyển từ nguồn 3307 sang đích trống; trên máy hiện tại script từ chối chạy lại vì đã dùng XAMPP.

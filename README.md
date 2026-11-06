# CineVerse — Đồ án Lập trình Java

Website bán vé xem phim tại rạp, gồm trang khách hàng và trang quản trị riêng, dùng chung backend Java Spring Boot và CSDL MariaDB/MySQL XAMPP.

- **Hướng dẫn cấu hình và khởi chạy đầy đủ:** [CineVerse/README.md](CineVerse/README.md#khởi-động-trên-windows--xampp)
- [Hướng dẫn bản nộp](CineVerse/HUONG-DAN-NOP-BAI.md)
- [Báo cáo Word kèm ảnh chức năng thật](CineVerse/docs/report/NhomXX_CineVerse_LapTrinhJava.docx)
- [Kiến trúc](CineVerse/docs/ARCHITECTURE.md) · [CSDL/ERD](CineVerse/docs/DATABASE_ERD.md) · [Kết quả kiểm thử](CineVerse/docs/TEST_PLAN.md)

## Chạy nhanh trên Windows

Cài JDK 21, Node.js 22.12+ hoặc 24, PowerShell 7, Git, XAMPP và Mailpit. Mở PowerShell 7:

```powershell
git clone https://github.com/khahuynh362006-a11y/JAVA_M.Kha-T.Dat_24E.git
cd JAVA_M.Kha-T.Dat_24E/CineVerse
pwsh -File scripts/setup-env.ps1
notepad .env
```

Đặt `XAMPP_ROOT=C:/xampp` theo nơi cài XAMPP. Giữ các mật khẩu và JWT ngẫu nhiên vừa sinh. Với máy chưa có database `cineverse`, chạy:

```powershell
pwsh -File scripts/setup-xampp-db.ps1 -XamppRoot 'C:/xampp'
pwsh -File scripts/setup-media.ps1
npm.cmd --prefix frontend ci
pwsh -File scripts/start-local.ps1 -MailpitExe 'C:/tools/mailpit.exe'
pwsh -File scripts/show-demo.ps1
```

Đổi đường dẫn Mailpit theo máy. Thêm `-PromptRootPassword` vào lệnh setup DB nếu root XAMPP có mật khẩu. Không nhập SQL thủ công: Flyway tạo bảng và seed tạo dữ liệu mẫu. Nếu CSDL đã tồn tại, bỏ qua setup DB; script không ghi đè dữ liệu cũ.

| Thành phần | Địa chỉ |
|---|---|
| Khách hàng | http://localhost:5173 |
| Quản trị | http://localhost:5174 |
| Java API | http://localhost:8080/api/v1 |
| Swagger | http://localhost:8080/swagger-ui/index.html |
| Mailpit | http://localhost:8025 |

Admin: `admin@cineverse.local`; khách: `free@cineverse.local`. Lấy mật khẩu bằng `show-demo.ps1`. Không có mật khẩu admin cố định trong Git. Dừng dịch vụ bằng `pwsh -File scripts/stop-local.ps1` từ thư mục `CineVerse`.

payOS mặc định tắt trong bản Git, cần ba khóa riêng của người chạy để thanh toán online. payOS dùng tiền ngân hàng thật. Không kèm `.env`, khóa, CSDL cá nhân, node_modules, target hoặc cache. Email thử dùng Mailpit, không cần Gmail/Brevo để đăng ký.

## Sinh viên và nhánh

Trường Cao Đẳng Công Thương TP.HCM — lớp CCQ2411E. Giảng viên: Nguyễn Minh Tuấn.

- Lê Thành Đạt — MSSV 2124110145 — nhánh `LeThanhDat`.
- Huỳnh Minh Kha — MSSV 2124110175.
- `main`: bản tích hợp CineVerse mới nhất. Các nhánh `backend/kha`, `frontend/dat` và thư mục [cinema-management](cinema-management/README.md) giữ nguyên để tham khảo lịch sử.

Bản cập nhật có xóa vĩnh viễn dữ liệu quản trị, chặn xóa lịch sử đặt vé/thanh toán, báo cáo Word và hướng dẫn cài mới. Kết quả xác minh: 83 kiểm thử Java, 2 kiểm thử trình duyệt cho lỗi phản hồi rỗng/xóa rạp, lint và build hai trang đạt; chi tiết trong TEST_PLAN. Docker Compose có cấu hình nhưng chưa được chạy xác minh.
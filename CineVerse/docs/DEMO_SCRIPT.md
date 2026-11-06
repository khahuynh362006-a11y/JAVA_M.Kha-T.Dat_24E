# Kịch bản demo 15 phút

Chuẩn bị: chạy start-local.ps1; dùng show-demo.ps1 xem mật khẩu local; mở khách http://localhost:5173, admin http://localhost:5174 và Mailpit ở các tab riêng. Cùng CSDL MariaDB XAMPP 3306. Chọn suất còn bán, tránh các ghế đã dùng bởi kiểm thử. payOS chưa có khóa đang tắt; không trình bày như đã kết nối ngân hàng.

| Phút | Thao tác | Điều cần chứng minh |
|---|---|---|
| 0–2 | Trang chủ, tìm kiếm có/không dấu, chi tiết phim, trailer | Giao diện cinematic tiếng Việt, catalog từ DB |
| 2–4 | Đăng ký rồi đăng nhập ngay | Không cần mở hộp thư hoặc xác thực email |
| 4–7 | Chọn rạp/ngày/suất, chọn ghế thường và VIP | Giá server, giữ ghế 8 phút |
| 7–9 | Mở checkout payOS, giải thích trạng thái chưa kết nối; xem vé mẫu đã phát hành từ hồi quy nội bộ | Không tạo giao dịch khi thiếu khóa; không gọi vé simulation là giao dịch payOS |
| 9–11 | Admin nhập mã vé hai lần | Lần đầu USED, lần hai bị từ chối |
| 11–13 | Tạo suất trùng phòng và giờ | Backend từ chối overlap |
| 13–15 | Dashboard, email/audit logs, giải thích test và giới hạn | Số liệu DB, không gọi mô phỏng là MoMo thật |

Nếu ghế bị giữ, chọn ghế khác; không sửa DB để ép thành công. Nếu email chưa tới, xem outbox/last_error. Nếu không có lịch, admin tạo suất tương lai. Không trình diễn giao dịch tiền thật.

Nhấn mạnh: JUnit dùng H2; kiểm thử browser mới dùng MariaDB XAMPP thật. 80 Java test đạt, hai trang build/lint đạt và kiểm thử độc lập phiên/cập nhật dữ liệu đạt. payOS cần khóa riêng và dùng tiền thật, chưa có end-to-end ngân hàng; Docker chưa được chạy. Thanh toán đến muộn không phát hành vé, chuyển nhân viên hỗ trợ.

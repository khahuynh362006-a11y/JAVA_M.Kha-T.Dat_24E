# Email và SMTP

**Cập nhật theo yêu cầu mới:** đã bỏ xác thực email đăng ký. Người dùng đăng ký rồi đăng nhập ngay; tài khoản cũ chưa xác thực cũng được phép đăng nhập. Không cần làm các bước Gmail/Brevo dưới đây để sử dụng tài khoản. Các công cụ cấu hình đã dừng. Email chào mừng, biên nhận, reset/đổi email vẫn có thể dùng SMTP nếu cần; không gửi VERIFY khi đăng ký nữa.

Local dùng Mailpit: SMTP 127.0.0.1:1025, giao diện http://localhost:8025. Xem MAIL_* trong .env.example. Nếu chạy bằng Compose, hostname là service trong mạng Compose; không dùng localhost để gọi container khác.

Email xác thực, reset mật khẩu, đổi email và biên nhận đặt vé sử dụng template HTML có plain text fallback. Nội dung do người dùng nhập được escape. Token ngẫu nhiên được hash trong DB, có hạn sử dụng và chỉ dùng một lần.

Outbox được ghi trong transaction nghiệp vụ. Worker chỉ gửi sau commit; claim có điều kiện để tránh hai worker lấy cùng bản ghi. Gửi lỗi retry với backoff, tối đa 5 lần rồi FAILED. Admin xem last_error và gửi lại. Không ghi secret hoặc token vào log ứng dụng.

## Kiểm tra
Đăng ký tài khoản mới, mở Mailpit, bấm xác thực và đăng nhập. Thử quên mật khẩu, dùng link reset rồi đăng nhập bằng mật khẩu mới. Đặt vé mô phỏng thành công, kiểm tra biên nhận có mã đơn, phim, suất và ghế. Callback lặp không được tạo receipt mới.

SMTP accept nghĩa là mail server đã nhận, không đảm bảo người nhận ngoài Internet đã đọc. Nếu worker crash sau SMTP accept nhưng trước khi đánh dấu SENT, email có thể gửi trùng. Đây là giới hạn at-least-once; không tuyên bố exactly-once SMTP.

SMTP Internet cần provider, credential, TLS và domain gửi hợp lệ do người triển khai cung cấp. Chưa kiểm chứng khả năng gửi tới hộp thư ngoài Internet.

## Bật gửi Gmail tự động

Chạy `pwsh -STA -File scripts/setup-gmail.ps1`. Cửa sổ nhập Gmail gửi thư và mật khẩu ứng dụng được che ký tự; không gửi mật khẩu vào chat.

1. Bật xác minh 2 bước cho tài khoản Google, tạo mật khẩu ứng dụng tên CineVerse tại https://myaccount.google.com/apppasswords. Theo [Google](https://support.google.com/mail/answer/185833), đây là mật khẩu ứng dụng 16 ký tự, không phải mật khẩu đăng nhập. Một số tài khoản bị quản trị hạn chế sẽ không có tùy chọn này.
2. Nhập Gmail gửi thư và mật khẩu ứng dụng, bấm **Gửi thư thử và kích hoạt**. Thao tác này gửi đúng một thư thử tới chính địa chỉ gửi; chưa phải email xác thực tài khoản.
3. Nếu SMTP chấp nhận thư, script lưu MAIL_HOST=smtp.gmail.com, MAIL_PORT=587, MAIL_AUTH=true, MAIL_TLS=true, MAIL_USERNAME/MAIL_FROM là Gmail đã nhập và MAIL_PASSWORD vào .env đã gitignore. Không sao lưu secret hoặc ghi ra console.
4. Backend được khởi động lại riêng; Spring Mail dùng STARTTLS bắt buộc và xác minh tên máy chủ TLS. Nếu restart lỗi, chạy scripts/restart-backend.ps1.
5. Mở Gmail (cả Spam), sau đó đăng ký trên website hoặc bấm **Gửi lại email xác thực** trên trang đăng nhập cho tài khoản cũ. Thư đã SENT vào Mailpit trước đây không tự gửi lại.

Cấu hình còn thiếu cho đến khi người sở hữu cung cấp Gmail/mật khẩu ứng dụng trong cửa sổ và gửi thử thành công. Không có bằng chứng Gmail thật trước bước đó. Khi dùng SMTP Internet, không chạy bộ E2E tạo email giả; chuyển về Mailpit cho kiểm thử local. Link xác thực hiện trỏ localhost nên mở trên chính máy chạy website.

## Cách khác: Brevo, thiết lập trong trình duyệt

Chạy `pwsh -File scripts/setup-email-web.ps1`; mở URL trong .runtime/email-setup-url.txt. Công cụ local có đường dẫn ngẫu nhiên, kiểm Host/loopback/Origin, không lưu hoặc hiển thị lại khóa trên trang. Chỉ gửi thư thử khi người dùng nhấn nút; chỉ lưu .env sau khi SMTP chấp nhận thư. Không cần đổi code backend để dùng relay SMTP.

Tạo tài khoản Brevo, xác minh sender và lấy SMTP Login cùng SMTP key (không phải API key). Nhập ba trường trên trang cấu hình. Công cụ dùng smtp-relay.brevo.com:587 với STARTTLS, gửi thư thử tới chính sender, lưu config rồi restart backend. Tài khoản cần được Brevo cho phép gửi; việc tạo tài khoản hoặc lấy key không tự đảm bảo quyền gửi/deliverability. Nếu dùng sender Gmail, Brevo có thể thay miền người gửi sang brevosend.com; tên miền riêng đã xác thực phù hợp cho vận hành thật.

Nguồn: [SMTP Brevo](https://help.brevo.com/hc/en-us/articles/7924908994450-Send-transactional-emails-using-Brevo-SMTP), [thay miền sender](https://help.brevo.com/hc/en-us/articles/16045394674066-Troubleshooting-Domain-authentication). Chưa có credential Brevo thật, chưa xác minh gửi tới Gmail. Không gọi trang cấu hình sẵn sàng là email đã được kích hoạt.

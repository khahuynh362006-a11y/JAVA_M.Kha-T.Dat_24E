# Thanh toán online payOS

Phương thức hiện tại là `PAYMENT_MODE=payos`. Đã viết tích hợp backend bằng SDK chính thức `vn.payos:payos-java:2.0.1`; chưa nhập khóa merchant, chưa gọi API tạo link thật và chưa chuyển tiền. `.env` đang để `PAYOS_ENABLED=false`, checkout báo chưa kết nối và không tạo giao dịch. Không tự chuyển sang mô phỏng.

**payOS không có Sandbox tiền ảo.** Theo [tài liệu môi trường test](https://payos.vn/docs/moi-truong-test/), thử nghiệm dùng production API, tài khoản xác minh CCCD và tài khoản ngân hàng thật. Không dùng các hướng dẫn/thẻ Test VNPAY cho payOS. Không suy luận “miễn phí dịch vụ” thành “tiền thanh toán là ảo”; chủ tài khoản cần kiểm tra điều khoản/biểu phí hiện hành trước khi bật.

## Kết nối trên máy

Nếu Windows báo không nhận lệnh `pwsh`, nhấp đúp `Cai-dat-payOS.cmd` trong thư mục gốc dự án. Launcher tự chọn PowerShell 7 cài trên máy hoặc bản runtime có sẵn của Codex, chuyển đúng thư mục dự án rồi chạy công cụ. Không cần thêm PATH hoặc chạy từ System32.

1. Đăng ký và hoàn tất xác minh tại [my.payos.vn](https://my.payos.vn), liên kết tài khoản ngân hàng của bạn.
2. Tạo kênh thanh toán cho CineVerse trong payOS; lấy **Client ID, API Key, Checksum Key** của chính kênh đó.
3. Chạy tại thư mục dự án: `pwsh -File scripts/setup-payos.ps1`. Công cụ ghi rõ tiền thật và yêu cầu nhập cụm kích hoạt. Cả ba khóa được nhập che ký tự; không gửi khóa vào chat hoặc lưu trong mã nguồn.
4. Script cập nhật `.env` được gitignore và restart backend. `pwsh -File scripts/setup-payos.ps1 -CheckOnly` chỉ xuất các cờ hiện diện, không in khóa. `ready=true` chỉ cho biết đã nạp đủ cấu hình, chưa chứng minh payOS chấp nhận khóa.
5. Khai báo webhook của kênh là `https://TEN-MIEN-API/api/v1/payments/payos/webhook`. payOS cần truy cập được HTTPS công khai; localhost không nhận webhook Internet. Khi triển khai, đặt `FRONTEND_URL`, `ADMIN_URL`, CORS và `SECURE_COOKIE=true` đúng các địa chỉ HTTPS. Tạo lại build với `VITE_ADMIN_URL` và `VITE_CUSTOMER_URL` nếu không dùng hai cổng local.

Đối soát qua API từ backend vẫn chạy khi demo local chưa có webhook công khai; người dùng có nút kiểm tra lại và scheduler xử lý đơn pending. Không cần mua tên miền để kiểm tra đơn local bằng query, nhưng nhận webhook liên tục cần endpoint HTTPS truy cập được. Không có tunnel nào tự được mở bởi dự án.

## Cách xử lý giao dịch

- Backend tạo order code ngẫu nhiên trong giới hạn số nguyên an toàn JavaScript, lưu ánh xạ tại `payos_links` trước khi gọi provider. Số tiền VND lấy từ snapshot booking, không nhân 100 như VNPAY.
- SDK ký yêu cầu và xác minh chữ ký phản hồi. Backend kiểm thêm order code, amount, link ID và checkout host `https://pay.payos.vn`.
- Return/cancel URL đưa về trang kết quả; tham số trình duyệt không được dùng để xác nhận đã thanh toán. Webhook phải qua xác minh HMAC của SDK rồi query trạng thái đã ký từ provider.
- Chỉ trạng thái `PAID` và tổng tiền nhận được đủ mới phát vé. `UNDERPAID/PENDING` chưa phát vé. Callback/query lặp không phát thêm vé. Đến tiền sau khi mất giữ ghế chuyển `PAYMENT_REVIEW`, không giành ghế đã bán cho người khác; staff xử lý hoàn tiền thủ công bên ngoài hệ thống.
- Timeout tạo link được giữ pending để đối soát, không tự gọi tạo lại và không khẳng định đã thất bại. Không log khóa, không có payout/rút tiền tự động.
- Admin thống kê payOS tách riêng dữ liệu Sandbox và mô phỏng cũ.

Nguồn: [Java SDK](https://payos.vn/docs/sdks/back-end/java/), [API](https://payos.vn/docs/api/). Kiểm thử chữ ký và nghiệp vụ dùng dữ liệu giả trong JUnit; chỉ được gọi là end-to-end payOS sau khi chủ tài khoản tự kết nối và hoàn tất giao dịch ngân hàng thật.

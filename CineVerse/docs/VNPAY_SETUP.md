# VNPAY Sandbox — cổng thanh toán thử nghiệm

Đây là kết nối tới VNPAY Sandbox thật, không phải ví do CineVerse tự mô phỏng. Người mua được chuyển sang trang VNPAY để nhập thông tin thẻ/OTP Test. Không tích hợp endpoint Production, không nạp tiền thật và không mua dịch vụ để làm đồ án.

## Bước 1: Nhận thông tin Test qua email

Mở [đăng ký Sandbox](https://sandbox.vnpayment.vn/devreg/), điền thông tin thật được yêu cầu và kiểm tra email để nhận **vnp_TmnCode** và **vnp_HashSecret**. Theo [tài liệu VNPAY](https://sandbox.vnpayment.vn/apis/docs/thanh-toan-pay/pay.html), thông tin kết nối được gửi về email đã đăng ký. Nếu chưa có thư, kiểm tra Spam hoặc liên hệ VNPAY; không dùng khóa được chép từ repository của người khác.

Nếu form báo “Không đúng định dạng Url” với localhost, điền `http://cineverse.localhost:5173/` ở ô Địa chỉ URL. Đã kiểm tra địa chỉ này khớp regex của form VNPAY và trả HTTP 200 từ Vite trên máy local. Subdomain .localhost vẫn trỏ về loopback, không cần mua tên miền và không công khai website ra Internet. Sau đăng ký vẫn sử dụng CineVerse tại http://localhost:5173 như trước; URL return backend giữ localhost:8080. Mã CAPTCHA do người dùng tự nhập.

Hai URL chính thức (đã kiểm tra HTTP 200 trong phiên phát triển): đăng ký ở trên và [tài liệu kết nối](https://sandbox.vnpayment.vn/apis/docs/thanh-toan-pay/pay.html). Trạng thái truy cập có thể thay đổi theo thời điểm/mạng.

## Bước 2: Nhập khóa trên máy

Trong PowerShell tại thư mục dự án:

```powershell
pwsh -File scripts/setup-vnpay.ps1
```

Nhập TmnCode và HashSecret từ email; HashSecret được che ký tự. Script giữ cấu hình khác, lưu PAYMENT_MODE=vnpay cùng VNPAY_* vào .env đã gitignore và restart backend. Không gửi HashSecret vào chat, không commit .env. Script không tạo giao dịch hay thu tiền. `pwsh -File scripts/setup-vnpay.ps1 -CheckOnly` chỉ báo có/thiếu khóa, không hiện khóa.

Return mặc định: http://localhost:8080/api/v1/payments/vnpay/return. Frontend: http://localhost:5173. Cả hai mở trên cùng máy. Không cần tên miền trả phí cho luồng return + query local này.

## Bước 3: Thanh toán thử như giao dịch thông thường

Đăng nhập CineVerse → chọn phim/suất/ghế → tạo **đơn mới** → thanh toán VNPAY Sandbox → chọn thẻ nội địa NCB. Thẻ Test theo [hướng dẫn chính thức](https://sandbox.vnpayment.vn/apis/docs/gioi-thieu/):

| Trường | Giá trị TEST |
|---|---|
| Ngân hàng | NCB |
| Số thẻ | 9704198526191432198 |
| Chủ thẻ | NGUYEN VAN A |
| Ngày phát hành | 07/15 |
| OTP | 123456 |

Chỉ nhập dữ liệu Test này trên cổng Sandbox. Không dùng thẻ thật. Nếu VNPAY cập nhật bộ thẻ Test, dùng thông tin trong tài liệu chính thức tại thời điểm test.

Sau khi VNPAY trả về, trang kết quả yêu cầu backend query giao dịch. Chỉ kết quả HMAC hợp lệ, đúng merchant/order/amount và trạng thái thành công mới phát hành vé. Nếu đang xử lý, bấm Kiểm tra lại; giới hạn đối soát mỗi đơn 15 giây. Scheduler cũng đối soát đơn pending mỗi 30 giây, tối đa 3 đơn/lượt. Không phát vé chỉ dựa vào redirect hoặc tham số thành công trên trình duyệt.

## IPN và tính toàn vẹn

IPN: `GET https://<public-host>/api/v1/payments/vnpay/ipn`, khai báo cho VNPAY nếu có HTTPS public. Khi chạy local chưa có public URL, query backend được dùng để đối soát; khi khách đóng trang vẫn có scheduler. Chưa cài hoặc mở tunnel trong phiên này.

URL checkout dùng API 2.1.0, HMAC SHA512 trên các trường đã sort/URL encode, số tiền VND nhân 100 và thời gian GMT+7. ID giao dịch mới chỉ gồm chữ/số. Return chỉ redirect; IPN kiểm chữ ký, merchant, giá và orderInfo. Callback lặp trả RspCode 02, không phát thêm vé. Thành công sau hết hạn/hủy ghi PAYMENT_REVIEW, không lấy ghế khách khác; chưa tự động hoàn tiền. Query dùng HTTPS endpoint Sandbox cố định, kiểm HMAC response và binding về đơn trước khi settle trong transaction.

## Trạng thái triển khai

Đã viết gateway/create/IPN/return/query/reconciliation, bảo vệ owner và bộ test tự động. Chưa có TmnCode/HashSecret riêng nên chưa thực hiện giao dịch qua VNPAY Sandbox thực tế. Checkout sẽ báo thiếu cấu hình và khóa nút trả tiền; không tự dùng khóa mẫu hoặc bật mô phỏng thay thế. Đơn tạo dưới provider cũ cần hủy giữ ghế và đặt đơn mới.

VNPAY chỉ được bật trong dev/test; bản production hiện fail closed. Sandbox có thể yêu cầu quyền merchant, IP/Return URL được cấp phù hợp; ready=true chỉ kiểm cấu hình hình thức, không đảm bảo provider đã chấp nhận tài khoản. Query lỗi/chưa rõ không được coi là thành công. Chi tiết test và log xem TEST_PLAN.md.

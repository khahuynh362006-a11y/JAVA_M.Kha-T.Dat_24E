# Cấu hình MoMo Sandbox

Mặc định là PAYMENT_MODE=simulation: không gọi MoMo, không thu tiền. Mô phỏng chỉ hoạt động với profile dev/test. Không dùng kết quả mô phỏng làm bằng chứng giao dịch MoMo.

## Phương án miễn phí cho đồ án

Theo yêu cầu của người dùng, không mua hosting/tên miền, không đăng ký gói trả phí hoặc nạp tiền thật. Mô phỏng local dùng được ngay và không cần tài khoản ngoài. Sandbox chỉ dùng bộ khóa Test và ví/ứng dụng MoMo Test; cấu hình không cho phép chuyển endpoint sang Production. Việc có quyền nhận bộ khóa Test do MoMo quyết định; ứng dụng không tạo khóa merchant thay người dùng.

## Lấy bộ khóa thử nghiệm

1. Truy cập [MoMo cho Doanh nghiệp](https://business.momo.vn/), chọn Đăng ký và hoàn tất thông tin được yêu cầu theo thực tế. Theo [hướng dẫn hồ sơ của MoMo](https://developers.momo.vn/v3/vi/docs/payment/onboarding/merchant-profile/), tài khoản đăng ký thành công có thể truy cập môi trường Test và Testing Credentials.
2. Mở phần tích hợp thanh toán/website, chọn môi trường Test, lấy **Partner Code**, **Access Key**, **Secret Key**. Không lấy bộ khóa Production hoặc khóa mẫu công khai của người khác. Nếu portal chưa cho quyền Test, liên hệ hỗ trợ MoMo theo portal; không xem đăng ký là bảo đảm merchant đã được cấp quyền.
3. Chỉ nhập khóa ở máy local bằng script bên dưới. Không gửi Secret Key vào chat hoặc commit vào Git.
4. Làm theo [hướng dẫn ví/ứng dụng MoMo Test](https://developers.momo.vn/v3/vi/docs/payment/onboarding/test-instructions/) để thực hiện giao dịch trên Sandbox.

## Nhập cấu hình một lần

Sau khi có bộ khóa Test và IPN HTTPS công khai, chạy từ thư mục dự án:

```powershell
pwsh -File scripts/setup-momo.ps1
```

Access Key và Secret Key được nhập có che ký tự. Script kiểm tra URL, lưu các biến MOMO_* và PAYMENT_MODE=momo vào .env đã gitignore, giữ nguyên cấu hình khác, rồi restart backend. Script không tạo giao dịch hoặc thu tiền. `-CheckOnly` chỉ hiện trạng thái có/thiếu các trường, không hiện khóa.

IPN chính xác: `https://<địa-chỉ-public>/api/v1/payments/momo/ipn`. Không dùng localhost cho IPN. Khi test local có thể dùng [Cloudflare Quick Tunnel](https://developers.cloudflare.com/tunnel/get-started/quick-tunnels/) để có địa chỉ tạm, không cần mua tên miền. Sau khi cài cloudflared từ nguồn chính thức, lệnh tham khảo là `cloudflared tunnel --url http://localhost:8080`. Đây là hướng dẫn, chưa cài hoặc chạy tunnel trong phiên này; tunnel public dịch vụ local nên chỉ mở trong buổi test và dừng khi xong. Địa chỉ tạm đổi sau khi khởi động lại thì cập nhật MOMO_IPN_URL. Nếu chưa có khóa/tunnel, giữ simulation để demo.

Redirect mặc định: `http://localhost:5173/thanh-toan/ket-qua`, mở trên cùng máy. `/api/v1/payment-config` trả mode/provider/ready/message, không trả khóa. ready=true chỉ nghĩa là đủ cấu hình hợp lệ về hình thức, chưa chứng minh MoMo chấp nhận merchant. Checkout khóa nút khi chưa ready hoặc lỗi tải cấu hình. Backend từ chối sớm khi thiếu khóa/IPN, không tạo order rỗng.

## Cấu hình
1. Đăng ký merchant Sandbox và lấy partnerCode, accessKey, secretKey từ MoMo.
2. Đặt PAYMENT_MODE=momo và các MOMO_* trong .env theo .env.example. Không commit secret.
3. Cấu hình redirect URL về trang kết quả; IPN URL phải là HTTPS công khai trỏ đến endpoint IPN trong Swagger. Dịch vụ local cần tunnel do bạn quản lý.
4. Khởi động lại backend. Đặt vé mới, chuyển đến payUrl và dùng ứng dụng/tài khoản thử nghiệm do MoMo cung cấp.
5. Đối chiếu orderId, requestId, amount, providerTransaction và trạng thái booking trong admin; chỉ CONFIRMED mới có vé.

## Tính toàn vẹn
Gateway cố định endpoint Sandbox, ký HMAC SHA256 theo thứ tự trường của MoMo. IPN xác minh chữ ký và liên kết partner/request/order/amount/orderInfo trước khi cập nhật. Mã thành công 0 mới thực hiện fulfillment; các trạng thái đang xử lý không được xem là thanh toán thành công. Order được khóa khi xử lý; callback lặp không sinh thêm vé hoặc receipt.

Query định kỳ đối soát đơn pending. Query response không có trường chữ ký theo tài liệu hiện dùng; ứng dụng dùng HTTPS tới endpoint cố định và đối chiếu định danh response. Không tin trạng thái từ redirect của trình duyệt. Lỗi mạng khi tạo đơn được giữ để đối soát, không tự tạo đơn thứ hai.

Thanh toán thành công sau khi ghế hết hạn/hủy chuyển PAYMENT_REVIEW. Nhân viên cần liên hệ khách và xử lý hoàn tiền bên ngoài; chưa có hoàn tiền tự động.

## Bằng chứng và giới hạn
JUnit kiểm tra HMAC, callback giả mạo, sai số tiền, idempotency và fulfillment. Chưa có merchant credential/IPN HTTPS nên chưa thực hiện MoMo Sandbox end-to-end. Docker cũng chưa được xác minh trên máy hiện tại.

Đã bổ sung test HTTP adapter (mock transport, không gọi MoMo) cho request captureWallet, chữ ký/giá server, binding partner/order/request, chống URL Production/ngoài Sandbox, merchant rejection, timeout và query. Merchant từ chối chắc chắn khi create sẽ ghi FAILED và trả ghế; lỗi mạng chưa rõ giữ PENDING để đối soát, không tạo giao dịch thứ hai. Log lần build mới: .runtime/logs/momo-sandbox-build.log. Bộ test browser vẫn dùng simulation khi chưa có khóa merchant; không gọi đó là kiểm thử MoMo Sandbox thực tế.

Tài liệu chính thức: [MoMo payment APIs](https://developers.momo.vn/v3/docs/payment/api/wallet/onetime/) và [query](https://developers.momo.vn/v3/docs/payment/api/payment-api/query/).

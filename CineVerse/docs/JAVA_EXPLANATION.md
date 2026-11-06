# Giải thích Java trong CineVerse

## Lớp và trách nhiệm
Controller nhận DTO, Bean Validation và principal; service giữ quy tắc nghiệp vụ. Repository JPA xử lý entity và khóa order/user. JdbcTemplate dùng cho sơ đồ ghế và truy vấn khóa rõ ràng. DTO record bất biến về cấu trúc, không lộ entity hoặc passwordHash cho client.

## OOP
Đóng gói nằm ở service và repository. PaymentGateway là interface; các implementation MoMo và simulation được chọn theo cấu hình. Đây là đa hình: PaymentService tạo đơn qua cùng hợp đồng mà không đưa HMAC vào controller. Dependency injection qua constructor giúp thay adapter trong test và làm rõ phụ thuộc.

## Transaction và concurrent access
@Transactional bao quanh giữ ghế, tạo đơn, thanh toán và phát vé. SELECT FOR UPDATE trên suất tạo điểm tuần tự hóa cho các thao tác trên cùng suất. Unique(showtime_id,seat_id) là hàng rào cuối chống ghế trùng. Snapshot giá được tính từ DB; client chỉ gửi seatId, không quyết định amount.

Khóa tồn tại đến commit/rollback. Một transaction giữ nhiều khóa có thể deadlock nếu thứ tự không thống nhất; các luồng ghế khóa suất trước khi đổi reservation. Cần tiếp tục đo contention/deadlock khi tăng tải, không xem test hai người là load test.

## Kiểu dữ liệu
Instant biểu diễn thời điểm UTC; hiển thị theo Asia/Ho_Chi_Minh. LocalDate dùng chọn ngày suất. JDBC DATETIME có thể trả Timestamp hoặc LocalDateTime nên helper chuyển đổi tường minh. long lưu VND nguyên để tránh sai số số thực; List chứa DTO ghế, Set phát hiện seatId lặp.

## Ngoại lệ và bảo mật
ApiException mang mã lỗi nghiệp vụ; exception handler trả response nhất quán. Spring Security kiểm JWT và role; service kiểm quyền sở hữu từng booking/order. BCrypt hash mật khẩu, SHA256 hash token ngẫu nhiên; hai loại này phục vụ mục đích khác nhau.

Refresh rotation thu hồi token cũ, phát token mới và phát hiện replay. Cookie refresh HttpOnly, SameSite Strict; frontend giữ access token trong bộ nhớ. Endpoint refresh/logout kiểm Origin. Không coi việc ẩn nút admin là kiểm soát quyền.

## Lập lịch và tính lặp an toàn
Scheduler trả ghế quá hạn và retry outbox. Payment callback có thể đến nhiều lần nên PAID là terminal và ticket/receipt chỉ tạo một lần. Thanh toán sau hủy/hết hạn chuyển PAYMENT_REVIEW. Email dùng outbox để tách transaction DB khỏi SMTP, chấp nhận khả năng gửi trùng sau crash.

## Kiểm thử
JUnit kiểm giá, quyền, hết hạn, phát vé lặp, late payment, token và gateway. H2 giúp kiểm thử nhanh nhưng không thay thế MySQL. Playwright dùng backend và MySQL thật để kiểm race ghế, CRUD admin, checkout và SMTP. Không khẳng định hệ thống phân tán đã được kiểm chứng chỉ từ một instance local.

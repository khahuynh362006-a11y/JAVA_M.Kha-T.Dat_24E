# BÁO CÁO ĐỒ ÁN CINEVERSE

Ngày cập nhật: 05/11/2026. Báo cáo kỹ thuật của bản local bán vé xem phim; thông tin trường, lớp, giảng viên và sinh viên do người nộp điền theo thực tế.

## 1. Tên đề tài và mục tiêu

CineVerse là website bán vé xem phim tại rạp. Khách chọn phim, rạp, suất, ghế, thanh toán và nhận vé QR; quản trị quản lý vận hành và soát vé. Mục tiêu đồ án là minh họa một luồng giao dịch nhất quán bằng Java full-stack.

## 2. Phạm vi

Ứng dụng local có catalog hư cấu, hai rạp và lịch seed để demo. Không kết nối kho ghế thương mại. Yêu cầu streaming ban đầu đã được thay bằng bán vé; trailer vẫn phục vụ giới thiệu phim.

## 3. Đối tượng sử dụng

Khách vãng lai tìm phim và suất; khách đã đăng nhập giữ ghế, mua vé, đánh giá; quản trị CRUD và kiểm soát vận hành. Không có quyền VIP mua đè ghế người khác.

## 4. Yêu cầu chức năng

Đăng ký và đăng nhập ngay (không xác thực email), đăng nhập, reset, hồ sơ; catalog và tìm kiếm; yêu thích và đánh giá; lịch chiếu; giữ ghế; thanh toán; vé; email; admin và audit. Danh sách API cụ thể nằm trong API_DOCUMENTATION.md và Swagger.

## 5. Yêu cầu phi chức năng

Tiếng Việt, responsive, kiểm quyền ở backend, đầu vào hợp lệ, dữ liệu bền vững, không bán trùng ghế. Không có bằng chứng load test hoặc cam kết SLA production.

## 6. Công nghệ

Java 21 và Spring Boot 3.5; MariaDB 10.4.32 của XAMPP cho local (MySQL 8.4 cho Compose), Flyway, JPA và JDBC; React 19 TypeScript strict, Vite, TanStack Query, Tailwind, Motion, Recharts. Các bản khóa dependency nằm trong pom.xml và package-lock.json.

## 7. Kiến trúc tổng thể

Modular monolith: một backend chia module auth/users/catalog/booking/payments/email/admin/shared; hai ứng dụng React riêng cho khách (5173) và admin (5174) gọi chung API 8080/CSDL XAMPP 3306. Cookie refresh riêng theo Origin; quyền ADMIN luôn kiểm ở API. Business logic ở service; DTO đi qua API; persistence không bị lộ nguyên entity. Xem ARCHITECTURE.md.

## 8. Tổ chức mã nguồn

backend/src/main/java chứa nghiệp vụ; resources chứa config, template và migration. frontend/src/features tổ chức theo tính năng; lib/api tập trung HTTP; styles giữ token. scripts hỗ trợ cấu hình, build và local runtime.

## 9. Thiết kế dữ liệu

cinemas → auditoriums → seats; showtimes liên kết phim/phòng; bookings và booking_items giữ snapshot; seat_reservations biểu diễn giữ/bán; payment_orders liên kết booking; tickets là quyền vào rạp. Users, tokens, outbox và audit hỗ trợ nền tảng. Xem DATABASE_ERD.md.

## 10. Migration và seed

Flyway V1 tạo nền tảng, V2 email token, V3 cinema booking, V4 hủy thư xác thực đăng ký đang chờ, V5 ánh xạ order code/link payOS. Có 32 bảng gồm lịch sử Flyway. Đã chuyển 31 bảng nguồn MySQL sang XAMPP, đối chiếu số dòng và bảo toàn nguồn/backup; xem XAMPP_SETUP.md. Seed chỉ chạy dev/test, không hardcode production admin. Lịch mẫu có thời hạn; admin thêm suất khi demo cũ. Giữ schema legacy để tránh phá dữ liệu, gỡ API phát phim đầy đủ.

## 11. Xác thực

Mật khẩu BCrypt; access JWT 15 phút; refresh cookie HttpOnly/SameSite Strict, lưu hash và rotation. Replay thu hồi phiên; token xác thực/reset một lần có TTL. Frontend không lưu access token lâu dài trong localStorage.

## 12. Phân quyền

Spring Security kiểm role; service kiểm owner booking/order. Truy cập ID của người khác bị từ chối. Admin API không chỉ dựa vào việc ẩn menu; bảo vệ last active admin để tránh tự khóa hệ thống.

## 13. Catalog và trải nghiệm

Backend tìm kiếm, phân trang, lọc thể loại/quốc gia/năm/lịch. Banner lấy DB; hero và artwork có nguồn rõ ràng. Trang chi tiết có trailer demo và lịch chiếu, nút mua vé thay luồng xem phim.

## 14. Rạp và suất chiếu

Admin tạo rạp, phòng và ma trận ghế. Suất lưu start/end, giá cơ bản và trạng thái. Kiểm trùng phòng theo duration + 15 phút; khóa phòng khi lập lịch. Không sửa suất đã có đơn hiệu lực.

## 15. Giữ ghế và cạnh tranh

Khách chọn 1–8 seatId khác nhau; service kiểm phòng và tính giá. Khóa suất trong transaction, khóa seat khi lấy thuộc tính và unique(showtime_id,seat_id) chống đặt trùng. Hai request cùng ghế chỉ một request thành công.

## 16. Hết hạn và hủy

Giữ 8 phút, đóng bán trước suất 10 phút. Scheduler và truy cập seatmap dọn giữ quá hạn. Khách hủy đơn chưa trả tiền để trả ghế. Giao dịch thanh toán thất bại/hủy cũng giải phóng ghế; không cho tự hủy vé đã trả tiền.

## 17. Đơn hàng và giá

Giá gồm basePrice và phụ thu ghế VIP, lưu snapshot booking_items. Client không gửi tổng tiền làm nguồn tin cậy. Một booking có một payment order; tạo lại checkout trả order đã tồn tại để tránh giao dịch lặp.

## 18. Thanh toán

PaymentGateway hiện chọn payOS online, SDK chính thức 2.0.1; kiểm chữ ký create/query/webhook và order code/amount/link ID. Webhook kích hoạt query trạng thái đã ký; chỉ PAID đủ tiền mới phát vé, UNDERPAID không phát vé. Return không phải bằng chứng đã trả. Timeout không tự tạo link lặp. payOS không có Sandbox; giao dịch dùng tiền ngân hàng thật. Chưa có khóa merchant nên kết nối đang tắt, chưa làm giao dịch thật; hướng dẫn PAYOS_SETUP.md. Simulation/VNPAY/MoMo Test chỉ để hồi quy dev/test; production chỉ nhận payOS được cấu hình.

## 19. Fulfillment và late payment

Khóa order, PAID terminal, fulfillment kiểm hold/reservation trước khi phát vé. Callback lặp không sinh thêm ticket/receipt. Thành công sau hết hạn/hủy chuyển PAYMENT_REVIEW, không lấy ghế người khác; chưa tự động hoàn tiền.

## 20. Vé điện tử và soát vé

Mỗi ghế một mã ngẫu nhiên và QR; khách chỉ xem vé của mình. Admin nhập hoặc dùng máy quét dạng bàn phím. Check-in khóa ticket, ISSUED → USED một lần; không có ứng dụng quét camera chuyên dụng.

## 21. Email và thông báo

Transactional outbox ghi cùng giao dịch, worker gửi SMTP sau commit. Retry/backoff rồi FAILED; admin có resend. HTML escape và plain fallback. Mailpit được dùng kiểm email local; crash sau SMTP accept có thể tạo thư trùng.

## 22. Quản trị và thống kê

CRUD phim/danh mục/rạp/phòng/ghế/suất, danh sách booking/ticket/payment, kiểm duyệt và audit. Admin là ứng dụng riêng; thay đổi catalog hiển thị ở trang khách khi focus hoặc sau khoảng 30 giây. Dashboard truy vấn DB, tách payOS, Sandbox và simulation. CSV escape công thức và giới hạn số dòng.

## 23. Bảo mật và media

Validation phía server, parameterized SQL và whitelist resource; khóa tài khoản/thu hồi phiên, kiểm Origin refresh/logout, rate limit. Upload giới hạn loại/magic bytes và đường dẫn UUID. Chỉ dùng trailer nguồn mở; ghi license tại MEDIA.md.

## 24. Kiểm thử và bằng chứng

JUnit dùng H2 MySQL mode và adapter mock có chủ đích; browser E2E dùng MySQL/Mailpit thật. Kiểm race ghế, owner, hết hạn, checkout, QR, callback trùng, SMTP, CRUD và responsive. Kết quả cuối cùng xem TEST_PLAN.md; không đồng nhất H2 với MySQL hoặc simulation với MoMo.

## 25. Triển khai và vận hành

README có lệnh setup/start/stop và tài khoản demo. Secret sinh ngẫu nhiên trong .env/.runtime được gitignore. Các tiến trình local bind loopback. Có Dockerfiles/Compose nhưng chưa chạy do thiếu Docker CLI/daemon; chưa triển khai public.

## 26. Hạn chế và hướng phát triển

Một instance; rate limit và token trailer trong bộ nhớ; danh sách vé/CSV có giới hạn. Cần thêm hoàn tiền, khuyến mãi, combo, đối tác rạp, quan sát hệ thống, backup/restore và load test trước vận hành thật. Tài liệu UML, Java, kịch bản demo và hỏi đáp đi kèm giúp bảo vệ thiết kế hiện có, không hứa chức năng chưa thực hiện.

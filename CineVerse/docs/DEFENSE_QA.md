# 45 câu hỏi bảo vệ đồ án

## 1. Vì sao chọn modular monolith?

Đồ án triển khai một backend để đơn giản vận hành, vẫn tách module và adapter theo trách nhiệm. Chưa có nhu cầu chia microservice.

## 2. Controller có chứa nghiệp vụ không?

Controller nhận DTO/principal và gọi service; giá ghế, quyền owner và fulfillment ở service.

## 3. Vì sao dùng DTO record?

Hợp đồng API rõ, tránh serialize quan hệ JPA và lộ passwordHash hoặc trường nội bộ.

## 4. JPA và JDBC dùng chung được không?

Có; cùng datasource và transaction manager. JPA thuận tiện cho entity, JDBC giúp thể hiện truy vấn khóa ghế rõ ràng.

## 5. Chống bán trùng bằng cách nào?

Khóa suất trong transaction và unique(showtime_id,seat_id). Frontend chỉ hiển thị, server quyết định cuối.

## 6. Hai người cùng bấm một ghế thì sao?

Một người giữ được; request còn lại nhận xung đột. Browser test thực hiện hai user đồng thời trên MySQL.

## 7. Tại sao không chỉ disable nút ghế?

Trạng thái trình duyệt có thể cũ và request có thể bị sửa; cần kiểm tra và ràng buộc ở DB.

## 8. Giá từ đâu?

Server đọc basePrice và loại ghế, cộng phụ thu rồi lưu snapshot. Không tin số tiền do client gửi.

## 9. Vì sao snapshot?

Đơn đã tạo phải phản ánh giá và ghế tại thời điểm đặt, không thay đổi theo catalog sau đó.

## 10. Giữ ghế bao lâu?

8 phút, tối đa 8 ghế/đơn và 3 đơn đang giữ mỗi tài khoản.

## 11. Hết hạn được dọn khi nào?

Scheduler 30 giây và thao tác seatmap/hold chạy cleanup theo suất; UI countdown chỉ hỗ trợ hiển thị.

## 12. Khách tự đổi đồng hồ có kéo dài hold không?

Không, hạn do server lưu và so sánh thời gian server.

## 13. Vì sao khóa showtime?

Tạo điểm tuần tự hóa cho việc giữ và phát ghế cùng suất; phạm vi này đơn giản cho đồ án nhưng có contention khi tải cao.

## 14. Khóa có thể deadlock không?

Có thể khi nhiều tài nguyên bị khóa khác thứ tự; phải kiểm soát thứ tự và đo khi tăng tải. Chưa có load test chứng minh mọi tình huống.

## 15. Transaction rollback bảo vệ gì?

Giữ booking/items/reservation và fulfillment cùng nhất quán, tránh ghi một phần khi nghiệp vụ thất bại.

## 16. Đơn thanh toán được tạo lặp?

Unique booking_id và kiểm order hiện có giúp trả cùng order; không tạo thanh toán mới cho mỗi click.

## 17. Callback trùng có thêm vé không?

Không; khóa order và PAID terminal, vé/receipt chỉ tạo một lần.

## 18. Redirect thành công có phát vé không?

Không. Trạng thái phải được backend xác minh từ gateway/IPN/query.

## 19. Thanh toán sau hết hạn thì sao?

PAYMENT_REVIEW, không phát vé hoặc lấy ghế khác; nhân viên xử lý hoàn tiền bên ngoài.

## 20. Thanh toán thất bại/hủy thì sao?

Đơn vé chưa thanh toán bị hủy và trả ghế. Thành công đến muộn sau đó cần hỗ trợ.

## 21. MoMo đã chạy thật chưa?

Chưa có merchant credential/IPN HTTPS nên chưa kiểm thử Sandbox end-to-end. Mô phỏng được ghi nhãn riêng.

## 22. HMAC giúp gì?

Xác minh dữ liệu callback theo secret và thứ tự trường đã định; còn phải kiểm liên kết order/request/amount.

## 23. Tại sao có query đối soát?

IPN có thể trễ/mất. Query giúp kiểm trạng thái pending qua endpoint cố định; không tự đoán thành công.

## 24. Có hoàn tiền tự động không?

Chưa có; đó là giới hạn đã ghi tài liệu.

## 25. QR có phải bí mật không?

Mã vé là quyền vào rạp, cần bảo vệ khỏi chia sẻ. Chỉ owner/admin được truy cập qua API.

## 26. Soát vé hai lần thế nào?

Khóa ticket, chuyển ISSUED sang USED; lần sau bị từ chối.

## 27. Vì sao không lưu mật khẩu plaintext?

BCrypt hash có salt và chi phí tính toán, giảm rủi ro lộ mật khẩu khi DB bị đọc.

## 28. Hash token và password khác nhau?

Token ngẫu nhiên entropy cao dùng SHA256; mật khẩu người dùng cần hàm chậm như BCrypt.

## 29. JWT có thời hạn bao lâu?

15 phút. Phiên còn kiểm user/sessionVersion; refresh dùng cookie rotation.

## 30. Refresh replay là gì?

Dùng lại refresh đã xoay có thể báo hiệu bị lấy cắp; hệ thống thu hồi phiên liên quan.

## 31. Chỉ ẩn menu admin đủ không?

Không; backend kiểm role và quyền đối tượng trên từng tài nguyên.

## 32. Người A đọc booking B được không?

Không, service so owner; test kỳ vọng 403.

## 33. CSRF xử lý thế nào?

API nghiệp vụ dùng Bearer token trong bộ nhớ. Refresh/logout dùng cookie và kiểm Origin; SameSite Strict hỗ trợ giảm cross-site.

## 34. SQL injection được ngăn thế nào?

Giá trị truy vấn được bind; tên bảng/resource dùng whitelist, không nối trực tiếp tùy ý từ input.

## 35. Vì sao outbox?

Không gửi SMTP bên trong transaction nghiệp vụ; ghi ý định gửi cùng DB rồi xử lý sau commit.

## 36. Email có exactly-once không?

Không; crash sau SMTP accept có thể gửi lại. Dedup nghiệp vụ giảm trùng tạo outbox, không loại mọi trùng SMTP.

## 37. Mailpit có chứng minh email Internet không?

Chỉ chứng minh SMTP local nhận mail, không chứng minh hộp thư Internet nhận hoặc đọc.

## 38. H2 pass có đủ không?

Không; dialect và khóa có khác. Browser test trên MySQL bổ sung bằng chứng runtime.

## 39. Responsive kiểm những cỡ nào?

375, 390, 768, 1366, 1440 và 1920 pixel; kiểm overflow, runtime errors và screenshot.

## 40. Docker đã kiểm chứng chưa?

Chưa; máy không có Docker CLI/daemon. Compose là cấu hình cần xác minh ở môi trường có Docker.

## 41. Media có hợp pháp không?

Trailer Blender Foundation CC BY 3.0 có attribution, poster SVG tự tạo và hero AI; không phát phim thương mại.

## 42. Tại sao còn schema subscription?

Giữ dữ liệu legacy khi đổi phạm vi; UI và API checkout hiện tại phục vụ vé, API full movie đã gỡ.

## 43. Có triển khai production ngay được không?

Chưa. Cần cấu hình merchant/SMTP thật, HTTPS, backup, quan sát, load test và quy trình vận hành/hoàn tiền.

## 44. Thống kê có phải số giả không?

Dashboard truy vấn DB; giao dịch simulation được tách khỏi doanh thu thật, dữ liệu E2E lưu thật vẫn có thể xuất hiện.

## 45. Phát triển tiếp ưu tiên gì?

Hoàn tiền và quy trình hỗ trợ, lịch đối tác, load test cạnh tranh, backup/restore và quản trị nhiều instance.


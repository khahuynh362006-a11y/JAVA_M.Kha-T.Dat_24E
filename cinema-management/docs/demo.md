# Hướng dẫn demo và bàn giao

## Trạng thái thực tế

Đã kiểm thử backend88test và27bước HTTP trên JAR; **chưa kiểm chứng qua giao diện của Đạt hoặc máy Đạt**. Static có26tệp rỗng. Không có quản trị tài khoản hoàn chỉnh, thanh toán, đồ ăn, khuyến mãi, email/SMS hay giữ ghế tạm.

## Khởi động trên máy khác

Từ thư mục có pom.xml: đặt JAVA_HOME/JDK và biến DB_* theo README. DB mới chạy schema.sql→seed.sql→migrations/V001__booking_idempotency.sql. DB đã có baseline chỉ áp dụng V001 một lần sau kiểm tra. `mvnw.cmd package`→`java -jar target/cinema-management-0.0.1-SNAPSHOT.jar`. Linux dùng./mvnw. Không cần ổD hoặc đường dẫn tài khoản của Kha. Tạo local-accounts theo README, chạy app thường bỏ profile. Health200 không thay thế kiểm tra login/CRUD.

## Postman

Import docs/postman/cinema.postman_collection.json và cinema.local.example.postman_environment.json; chọn privateenvironment, điền credential riêng. Cookie jar bật. Chạy nhóm01→09 theo thứ tự; các nhóm07/08 giữ lịch sử demo có chủ đích, không reset seed. Nhóm09 chọn statisticsFrom/To bao gồm hai đầu, tối đa31ngày. Collection151request có assertion nhưng **chưa chạy trực tiếp trong Postman**; backend đã được kiểm tra bằng JUnit/HTTP/JAR độc lập.

## Kịch bản trình bày

1. ADMIN login, me; lưu token mới sau login. Tạo genre/phim, thử validation và xóa đang có liên kết.
2. Tạo hai phòng, bulk ghế; chuyển A2 thànhVIP trước khi có suất. Đọc sơ đồ và chứng minh A1 ở hai phòng hợp lệ.
3. Tạo suất, xem thời lượng snapshot/giờ kết thúc +07; thử thiếu15phút bị409 và đủ15phút được phép; qua nửa đêm.
4. STAFF đặt STD+VIP, tổng từ server; thử cùng ghế bằng key khác409, retry key cũ200 cùng booking.
5. ADMIN hủy, đặt lại key mới, hủy lặp cũ không làm mất booking mới. Không mô phỏng hoàn tiền.
6. Xem HATEOAS link đúng vai trò; STAFF gọi thống kê403.
7. Thống kê tính tay: xem evidence/statistics-evidence.json, chọn fixture.day/movieA. Có2phiếu CONFIRMED/3ghế/260000VND, một sự kiện hủy2ghế/180000, snapshot4→75%. Các dữ liệu fixture SQL là tổng hợp kiểm thử, không phải tình hình kinh doanh hay dự báo rạp.
8. Mở bằng chứng concurrency JSON: các request thật giữ2MySQLconnections; không dùng collection tuần tự làm bằng chứng đồng thời.

## Kết quả và giới hạn

DB cài mới: cinema_management_test_final_20261028_103848, MySQL8.4.3/3306, baseline+seed+V001, Hibernatevalidate. JAR dùng cổng thử riêng và đã dừng; không dừng app cổng8080. Tài khoản ngẫu nhiên minh chứng đã vô hiệu hóa/xóa hash, không dùng để đăng nhập. Fixture/history được giữ do trigger, không DROP/reset hay xóa dữ liệu thật.

Định nghĩa thống kê ở statistics.md; thiếu capacity_snapshot quá khứ trảnull, chưa có cơ chế chốt tại giờ bắt đầu. Giá trị booking không phải doanh thu. Chưa benchmark dữ liệu lớn; không tự thêm index/migration. Quyết định Kha còn cần duyệt: D-01…D-12, nhất là hủy STAFF/tra cứu đổi ca, bảo vệ ADMIN cuối cùng, VIP/giới hạn ghế/tuổi và chốt sức chứa. ChecklistUI ở integration-checklist.md; họ tên/MSSV/giảng viên/lớp cho Word cần nhóm bổ sung.

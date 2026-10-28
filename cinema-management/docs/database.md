# Thiết kế database MySQL cho MVP rạp phim

Ngày: 27/10/2026. Bản thiết kế đã thử trên MySQL 8.4.3; các chính sách D-01 đến D-12 vẫn **Đề xuất chờ Kha duyệt**. Việc tạo schema thử không đồng nghĩa đã duyệt các chính sách đó.

## 1. Căn cứ, phạm vi và tình trạng môi trường

Đã đọc `business-rules.md`, `screens.md`, README, cấu trúc và các file SQL/migration hiện có. Không có AGENTS.md trong dự án/các thư mục cha đã kiểm tra; README trống. `schema.sql`, `seed.sql` ban đầu trống, thư mục migrations chưa có migration nào. Thư mục dự án chưa là Git repository. Không tạo lại project, không sửa Java/frontend/cấu hình backend hoặc hai tài liệu nghiệp vụ nguồn.

Giữ đúng **11 bảng**: users, roles, user_roles, movies, genres, movie_genres, rooms, seats, showtimes, bookings, booking_seats. Không thêm bảng thanh toán, khách hàng, giữ ghế tạm hay vé QR. Baseline cho database trống, không phải migration cho database shop cũ. Không tạo migration giả khi chưa có schema cũ được áp dụng.

Tên dự kiến dùng sau: `cinema_management`. Chưa khởi tạo tên này trên Laragon đang dùng. Lúc kiểm tra, cổng 3306 không nhận kết nối. Đã dùng binary MySQL Laragon `C:/laragon/bin/mysql/mysql-8.4.3-winx64/bin/` để chạy **instance tách biệt** tại `127.0.0.1:13316`, thư mục dữ liệu thử `C:/Users/khahu/AppData/Local/Temp/cinema-db-validation-isolated-20261027/data`. Không dùng datadir Laragon chính, không đọc/sửa/xóa database shop hoặc database khác.

Database thử được giữ để xem lại:

- `cinema_management_test_20261027_1449`: lần nhập baseline và seed đầu tiên, trước bổ sung bảo vệ chuyển trạng thái chi tiết một chiều. Chỉ là bản thử sơ bộ, không dùng làm chuẩn cuối.
- `cinema_management_test_20261027_1455`: schema cuối, seed và bộ 38 kiểm tra thực tế đã đạt. Thư mục dữ liệu giữ sau khi dừng instance thử, không xóa database.

## 2. Các lựa chọn thiết kế và lý do

### 2.1 Quy tắc đã xác nhận và đề xuất

**Đã xác nhận theo yêu cầu:** một rạp nhiều phòng, vai trò ADMIN/STAFF; thời lượng dương; ghế đúng phòng/suất; không trùng ghế hiệu lực; hủy giải phóng ghế và giữ lịch sử; giá do backend tính và lưu lúc đặt; lịch cần nghỉ 15 phút và chống xung đột đồng thời. Email, mã vai trò, mã phòng, mã ghế trong phòng phải duy nhất.

**Đề xuất chờ Kha duyệt đã được thể hiện trong schema thử:** ACTIVE/INACTIVE; SCHEDULED/CANCELLED cho suất; CONFIRMED/CANCELLED cho đơn; tuổi nội bộ 0–18; hai giá thường/VIP với VIP không thấp hơn thường; VND nguyên; đặt chỗ xác nhận ngay, không giữ ghế tạm; hủy toàn bộ; cấm sửa/xóa lịch sử. Danh sách cuối tài liệu cần duyệt trước triển khai backend thật.

### 2.2 Kiểu dữ liệu, chuỗi và định danh

- Engine InnoDB, bộ ký tự utf8mb4, collation mặc định `utf8mb4_0900_ai_ci`. Yêu cầu MySQL >= 8.0.16 để CHECK được thực thi; đã chạy trên 8.4.3, không tuyên bố tương thích MariaDB/MySQL 5.7.
- ID là BIGINT UNSIGNED AUTO_INCREMENT; bảng nối dùng PK ghép. ID không đổi sau khi tạo. Không dùng tên phim làm khóa.
- Email/username/tên thể loại so sánh không phân biệt hoa thường và dấu theo collation đã chọn. **Đề xuất chờ Kha duyệt:** backend trim, chuẩn hóa email/username trước lưu; email theo địa chỉ ứng dụng chấp nhận, mã kỹ thuật chuyển uppercase và chỉ cho ký tự hợp lệ. CHECK hiện chỉ chặn rỗng, không kiểm tra định dạng email/URL.
- Mã phòng/phim/ghế/đơn dùng ASCII không phân biệt hoa thường. `roles.code` dùng `ascii_bin`, chỉ chấp nhận đúng ADMIN/STAFF viết hoa. UNIQUE chống trùng, nhưng chuẩn hóa khoảng trắng vẫn là trách nhiệm backend.
- Tìm chứa từ trong tên phim không được đảm bảo tăng tốc bởi chỉ mục B-tree `title`; index hỗ trợ lọc/prefix/sắp xếp. Chưa thêm FULLTEXT cho MVP.

### 2.3 Tiền và thời gian

- Giá suất/ghế: DECIMAL(12,0), tổng đơn: DECIMAL(14,0), đơn vị VND. Không dùng FLOAT/DOUBLE; backend dùng BigDecimal và cộng các đơn giá chính xác.
- CHECK chặn giá âm và VIP thấp hơn thường. MySQL có thể làm tròn đầu vào khi ép sang DECIMAL(...,0); **backend phải từ chối tiền lẻ VND trước INSERT**, không tuyên bố CHECK phát hiện được phần lẻ đã bị ép kiểu.
- Dùng DATETIME cho lịch, DATETIME(6) cho audit. Tất cả giá trị là giờ địa phương Việt Nam `Asia/Ho_Chi_Minh`, không trộn UTC vào cùng cột. Mỗi kết nối SQL/backend đặt session `time_zone='+07:00'`; DATETIME tự nó không chứa múi giờ và không tự chuyển đổi như TIMESTAMP.
- API sau này phải ghi offset +07:00/định nghĩa timezone; không nhận chuỗi thời gian mơ hồ. Lịch đến phút; audit đến microsecond. Lọc ngày bắt đầu theo khoảng `[đầu ngày, đầu ngày kế tiếp)`; không dùng DATE(column) nếu muốn tận dụng chỉ mục.
- `duration_minutes_snapshot`, `minimum_age_snapshot` lưu thông tin áp dụng của suất. CHECK đảm bảo kết thúc = bắt đầu + đúng thời lượng chụp, giây bắt đầu/kết thúc bằng 0. Thời gian nghỉ không cộng vào `ends_at`.
- `COMPLETED` là trạng thái hiển thị suy ra khi giờ hiện tại >= ends_at và suất không hủy, không lưu trong status. Đang chiếu cũng là giai đoạn suy ra. Backend còn phải kiểm tra giờ bắt đầu/cửa sổ hủy.

### 2.4 Cột kỹ thuật có chủ đích

| Cột/nhóm | Lý do cần |
|---|---|
| bookings.room_id; booking_seats.showtime_id/room_id | Dư thừa có kiểm soát để FK ghép bắt đúng suất và cùng phòng; không phụ thuộc SELECT Java |
| UNIQUE showtimes(id,room_id), seats(id,room_id), bookings(id,showtime_id,room_id) | Khóa đích rõ ràng cho FK ghép, tương thích yêu cầu khóa tham chiếu của MySQL 8.4 |
| booking_seats.booking_status | Trạng thái sao chép do trigger quản lý; generated column không truy vấn bảng cha được |
| booking_seats.active_seat_id | Generated STORED; tạo khóa ghế hiệu lực có điều kiện mà không cần bảng giữ ghế |
| price_version và price_version_snapshot | Backend phát hiện giá đã đổi giữa lúc xem và lúc xác nhận; phiên bản do service tăng, không tự tăng bởi SQL hiện tại |
| Các cột *_snapshot và unit_price/total_amount | Bảo toàn tên/phòng/người tạo/loại ghế và giá lúc tạo, không tính lại lịch sử từ dữ liệu hiện tại |
| capacity_snapshot | Đề xuất D-12: sức chứa sử dụng của suất chốt khi bắt đầu, NULL trước đó; service chịu trách nhiệm chốt, không có scheduler/trigger tự làm |
| cancelled_at/by/reason, created_at/by | Dấu vết hủy và người tạo; không cần bảng audit riêng cho MVP |

## 3. Quan hệ, khóa và quy tắc xóa

| Quan hệ | Cách bảo vệ |
|---|---|
| users n–n roles | user_roles PK(user_id,role_id), hai FK |
| movies n–n genres | movie_genres PK(movie_id,genre_id), hai FK |
| rooms 1–n seats; movies/rooms 1–n showtimes | FK theo ID; mã ghế và vị trí không lặp trong phòng |
| showtimes 1–n bookings | FK bookings(showtime_id,room_id) -> showtimes(id,room_id) |
| bookings 1–n booking_seats | FK chi tiết(booking_id,showtime_id,room_id) -> bookings(id,showtime_id,room_id) |
| seats 1–n booking_seats (qua nhiều suất/lịch sử) | FK chi tiết(seat_id,room_id) -> seats(id,room_id) |
| users 1–n bookings/showtimes audit | FK người tạo/hủy; người hủy được NULL chỉ khi chưa hủy |

**Tất cả FK đều ON DELETE RESTRICT, ON UPDATE RESTRICT**, không dùng CASCADE xóa dữ liệu lịch sử. Muốn xóa phim chưa có suất phải gỡ các dòng movie_genres hợp lệ trước trong giao dịch; không xóa thể loại đang được phim dùng. Muốn xóa phòng mới có ghế nhưng chưa có suất phải kiểm tra chính sách và xóa ghế chưa liên kết trước. Với lịch sử đặt chỗ, trigger chặn DELETE cả bookings và booking_seats, kể cả đơn đã hủy.

UNIQUE(bookings.code), email, username, role code, movie code, genre name, room code, (room_id,seat.code), (room_id,row_label,seat_number) và hai PK bảng nối ngăn trùng định danh. UNIQUE(booking_id,seat_id) chỉ chống lặp trong **một đơn**, không phải cơ chế chống trùng giữa các đơn.

Chỉ mục phục vụ: lịch theo phòng/trạng thái/giờ; lịch theo ngày và phim/ngày; đơn theo suất/trạng thái, người tạo/thời điểm; tra cứu mã; phim trạng thái/tên; bảng nối theo thể loại. Các FK có index đầu cột phù hợp; không dựa vào FK tham chiếu index không duy nhất.

## 4. Chống đặt trùng ghế và hủy giữ lịch sử

### 4.1 Khóa ghế hiệu lực

`booking_seats.active_seat_id` được MySQL tính:

```text
booking_status = CONFIRMED -> seat_id
booking_status = CANCELLED -> NULL
UNIQUE(showtime_id, active_seat_id)
```

MySQL cho nhiều NULL trong UNIQUE. Vì vậy cùng suất/ghế chỉ có một chi tiết CONFIRMED, nhưng giữ được nhiều lần đặt đã CANCELLED. Cùng ghế ở suất khác vẫn được dùng. Generated column không được client gán giá trị; ứng dụng không sửa booking_status trực tiếp.

### 4.2 Đồng bộ trạng thái, tránh lách khóa

| Trigger | Trách nhiệm |
|---|---|
| bi_booking_seats_status | Khóa dòng đơn cha FOR UPDATE, chỉ cho thêm vào đơn CONFIRMED; ép trạng thái chi tiết theo cha, không tin trạng thái client gửi |
| bu_booking_seats_history | Chặn sửa ID/ngữ cảnh/ghế/giá/thông tin chụp; kiểm tra trạng thái khớp cha; cấm chi tiết CANCELLED quay lại CONFIRMED ngay cả với snapshot đọc cũ |
| bd_booking_seats_history | Chặn xóa chi tiết để giữ lịch sử |
| bi_bookings_state | Đơn mới phải CONFIRMED |
| bu_bookings_history | Bất biến mã/ngữ cảnh/người tạo/thời điểm/tổng/thông tin chụp; đơn đã hủy không được phục hồi/ghi đè thông tin hủy |
| au_bookings_cancel | Khi cha CONFIRMED -> CANCELLED, cập nhật mọi chi tiết thành CANCELLED trong cùng statement/giao dịch |
| bd_bookings_history | Chặn xóa đơn |

Khi hủy, chỉ UPDATE đơn cha cùng thông tin hủy. Trigger thay đổi chi tiết -> active_seat_id thành NULL -> ghế có thể được đặt lại **sau khi giao dịch hủy commit**. ROLLBACK khôi phục cả trạng thái và khóa ghế. Không DELETE chi tiết; không tự set NULL khóa ghế; không cập nhật booking_seats trước cha.

Trigger INSERT khóa đơn cha để loại race thêm chi tiết trong lúc hủy. Khóa UNIQUE InnoDB bảo vệ giữa hai đơn khác nhau: người thứ hai chờ người đầu; nếu người đầu commit thì lỗi 1062, nếu rollback thì người thứ hai có thể thành công. Bắt deadlock/lock timeout và rollback toàn bộ giao dịch tại service; không giữ đơn thiếu ghế.

Đây là thiết kế chủ động dùng trigger thay cho ON UPDATE CASCADE trạng thái: MySQL có hạn chế CASCADE trên cột nền của generated STORED; mọi FK ở đây dùng RESTRICT.

**Giới hạn phải biết:** schema không tự chứng minh mỗi đơn có >=1 ghế, tổng đúng bằng SUM(unit_price), giá chụp đúng bảng giá, ghế đang ACTIVE, người tạo có quyền, đơn tạo trước giờ chiếu hay hủy trong cửa sổ hợp lệ. Trigger không thay thế service cho các điều kiện này. Một người có quyền SQL trực tiếp vẫn có thể INSERT thêm chi tiết vào đơn CONFIRMED; ứng dụng phải chỉ cho phép tạo trọn đơn trong một giao dịch, không cung cấp endpoint thêm ghế vào đơn cũ. Người có quyền DBA có thể thay đổi/bỏ constraint; không dùng root làm tài khoản runtime.

## 5. Không trùng lịch 15 phút và giao dịch backend cần làm

### 5.1 Database bảo đảm gì

CHECK bảo vệ thời lượng dương, giờ kết thúc chính xác theo snapshot và giá trị trạng thái/giá hợp lệ. FK bảo vệ phim/phòng tồn tại và ngữ cảnh các đơn. **Không có UNIQUE hay CHECK nào ở schema này ngăn được mọi khoảng lịch chồng lấn.** Truy vấn kiểm tra seed chỉ phát hiện dữ liệu vi phạm, không tự ngăn việc ghi.

Điều kiện giao nhau (chỉ xét suất SCHEDULED, bỏ ID đang sửa):

```text
existing.starts_at < new.ends_at + 15 phút
AND existing.ends_at + 15 phút > new.starts_at
```

Đủ đúng 15 phút là hợp lệ; không chỉ so sánh ngày hoặc giờ bắt đầu. Suất qua nửa đêm vẫn so bằng DATETIME đầy đủ.

### 5.2 Chiến lược khóa đề xuất

**Đề xuất chờ Kha duyệt về triển khai service:** mọi đường tạo/sửa/hủy lịch phải khóa **dòng phòng tồn tại**, kể cả phòng đang chưa có suất; không dựa vào khóa các dòng suất chưa tồn tại. Thực hiện trong giao dịch, isolation READ COMMITTED hoặc đọc khóa/current read phù hợp; không dùng consistent-read cũ để quyết định ghi.

Thứ tự khóa chung khi cần nhiều đối tượng: phim theo ID tăng -> phòng theo ID tăng -> suất theo ID tăng -> đơn theo ID tăng -> ghế theo ID tăng. Bước đọc đầu để biết ID chỉ là gợi ý; sau khóa phải kiểm tra lại ngữ cảnh. Nếu ngữ cảnh phim/phòng đã đổi so với lần đọc đầu, rollback và đọc lại; không khóa thêm theo thứ tự ngược. Khi chuyển suất giữa phòng/phim, khóa cả nguồn và đích theo thứ tự trên. Đường đổi trạng thái phim/phòng, sửa sơ đồ, đặt/hủy đơn cũng tuân thủ thứ tự tương thích để không lách quy tắc.

**Tạo/sửa lịch:** BEGIN -> khóa phim/phòng -> xác minh hoạt động -> khóa và đọc lại suất nếu sửa -> kiểm tra chưa bắt đầu và lịch sử đặt chỗ -> truy vấn giao nhau bằng dữ liệu hiện tại dưới khóa phòng -> tính ends_at/snapshot -> INSERT/UPDATE -> COMMIT. Khóa phòng được giữ tới commit, kể cả khi không có kết quả truy vấn giao nhau.

**Tạo đặt chỗ:** BEGIN -> khóa phim/phòng/suất theo thứ tự -> kiểm tra chưa hủy/chưa bắt đầu, trạng thái sử dụng và phiên bản giá -> khóa các ghế -> xác minh đúng phòng/đang dùng/không lặp -> tính từng đơn giá và tổng -> INSERT bookings với tổng cuối cùng -> INSERT toàn bộ chi tiết -> COMMIT. Lỗi ở bất kỳ ghế nào, gồm 1062/1452/3819/deadlock/timeout, phải ROLLBACK toàn bộ; không sửa total sau khi lưu vì trigger giữ bất biến.

**Hủy đơn:** khóa phòng/suất rồi đơn theo thứ tự -> kiểm tra quyền, giờ server và lý do -> nếu đã hủy trả trạng thái cũ -> UPDATE cha và metadata -> trigger giải phóng chi tiết -> COMMIT. Không tác động đến đơn mới của cùng ghế khi hủy lặp đơn cũ.

**Hủy suất:** ADMIN kiểm tra giờ và lý do dưới khóa phòng/suất -> khóa các đơn theo ID -> UPDATE hủy từng đơn còn hiệu lực (trigger đồng bộ) và hủy suất trong **một** giao dịch -> COMMIT. Nếu bất kỳ bước thất bại thì rollback tất cả. Database không tự cascade hủy suất sang đơn.

**Đổi giá:** khóa suất theo cùng thứ tự, kiểm tra điều kiện -> cập nhật hai giá và tăng price_version cùng statement -> commit. Request đặt mang phiên bản cũ phải được từ chối và thông báo giá mới để xác nhận lại. Đơn đã tạo dùng snapshot, không join giá hiện tại để hiển thị tiền cũ.

**Chốt sức chứa:** capacity_snapshot còn NULL trước giờ bắt đầu; service đảm bảo đếm/chốt dưới khóa cùng phòng/ghế trước thao tác sau giờ bắt đầu có thể đổi sức chứa. Không chỉ chạy tác vụ định kỳ muộn rồi lấy sức chứa đã thay đổi. Phương án đơn giản hơn cần Kha duyệt: chốt sức chứa ngay lúc tạo suất và cấm thay đổi khả dụng ghế khi còn suất tương lai. Chưa thực hiện lựa chọn thay thế này trong schema.

## 6. Khởi tạo trên MySQL Laragon

1. Bật MySQL Laragon, xác định đúng host/port/user; kiểm tra VERSION(), SHOW DATABASES, quyền và phiên bản trước khi ghi. Không tự dùng cổng 13316 như cổng Laragon thật.
2. Khi đã duyệt chính sách, tạo **database mới trống** `cinema_management` (nếu đã tồn tại phải kiểm tra, không ghi đè). Chọn InnoDB/utf8mb4_0900_ai_ci, hoặc dùng lệnh CREATE DATABASE ở phiên quản trị. Schema/seed cố ý không chứa CREATE/DROP DATABASE hay USE để không điều hướng nhầm.
3. Trong HeidiSQL chọn đúng database mới, mở/chạy toàn bộ `database/schema.sql` bằng chế độ hỗ trợ DELIMITER/trigger. Hoặc dùng mysql CLI từ thư mục dự án:

```text
mysql --host=127.0.0.1 --port=3306 --user=<user> --password --default-character-set=utf8mb4 cinema_management
mysql> SOURCE database/schema.sql;
mysql> SOURCE database/seed.sql;
```

`--password` không kèm giá trị sẽ hỏi tại prompt. Không lưu mật khẩu trong README, command history hay Git. Dùng đúng đường dẫn tới mysql.exe nếu chưa có PATH.

4. Chạy schema đúng một lần: không dùng IF NOT EXISTS để che schema không tương thích; không chạy với `--force`. DDL có implicit commit, nên lỗi giữa chừng có thể để lại một phần bảng/trigger; không tự xóa sửa lại, kiểm tra nguyên nhân và dùng database thử mới hoặc migration được duyệt. Không nhập vào database shop cũ.
5. Seed chạy một lần sau schema, gồm 2 vai trò, 3 thể loại, 3 phim hư cấu, 2 phòng, 16 ghế và 4 suất vào ngày kế tiếp tính theo session +07:00. Hai suất cùng phòng cách nhau đúng 15 phút. Không có người dùng/mật khẩu/đặt chỗ thật. Chạy lại seed sẽ báo trùng, không phải upsert.
6. Seed không tạo tài khoản đăng nhập. Backend sau này cung cấp quy trình tạo ADMIN/STAFF với BCrypt, gán user_roles và ACTIVE khi có hash đúng. SQL chỉ kiểm tra độ dài hash tối thiểu, không chứng minh hash BCrypt hợp lệ. User INACTIVE với password_hash NULL chỉ dùng cho fixture thử, không cho đăng nhập.
7. Sau này dùng tài khoản ứng dụng quyền tối thiểu; không cấp ALTER/DROP/TRIGGER, không vô hiệu hóa FK/CHECK để sửa lỗi. Cấu hình backend và tài khoản SQL không được tạo/sửa trong nhiệm vụ này.

### Chạy kiểm tra tái lập trên database thử

`verify.sql` chỉ cho chạy ca ghi trên tên `cinema_management_test_*`; các SELECT metadata đầu file chỉ đọc. Cần CREATE ROUTINE/ALTER ROUTINE trong DB thử; các procedure kiểm thử được gỡ khi thành công, fixture nằm trong giao dịch và rollback. Nếu lỗi, helper có thể còn do DDL đã commit; dừng và xem lỗi, dùng DB thử mới, không --force. Không dùng verify trên dữ liệu thật dù đổi tên database để vượt guard.

Runner thêm trong phạm vi CSDL `database/test_database.py` dùng Python chuẩn và mysql.exe, không cần cài connector. Runner tự CREATE một DB thử mới, không DROP/reset DB đã có, nhập schema/seed/verify và chạy hai kết nối thật. Chỉ chấp nhận tên prefix thử, giữ fixture race để kiểm tra sau. Ví dụ từ thư mục dự án:

```text
python database/test_database.py --mysql C:/laragon/bin/mysql/mysql-8.4.3-winx64/bin/mysql.exe --host 127.0.0.1 --port 3306 --user <user> --database cinema_management_test_new_run
```

Chỉ chạy khi đã xác định server/port là môi trường được phép tạo DB thử. Runner không nhận password trên command line; có thể dùng MYSQL_PWD tạm trong môi trường phiên chạy rồi xóa khỏi phiên, không lưu ra file. Không gọi runner với tên DB đã tồn tại. Có thể dùng mysql CLI hỏi mật khẩu và SOURCE verify.sql thủ công cho 35 ca SQL, rồi chạy hai phiên theo hướng dẫn cuối verify.sql.

## 7. Kết quả kiểm chứng thực tế

Ngày 27/10/2026, MySQL Community 8.4.3, InnoDB, instance thử cổng 13316, DB `cinema_management_test_20261027_1455`:

| Nhóm | Kết quả thực tế |
|---|---|
| Schema + seed từ trống | Thành công, đúng 11 bảng, 7 trigger, 16 ghế/2 phòng, 4 suất |
| Metadata và dữ liệu mẫu | Có PK/FK/UNIQUE/CHECK; engine/collation đúng; không có lịch seed trùng kể cả nghỉ 15 phút |
| Trùng email/role/phòng/ghế, quan hệ thể loại | Bị chặn bằng 1062; thể loại thiếu 1452 |
| Thời lượng 0, giá âm, kết thúc sai/thời lượng không khớp | Bị chặn bằng CHECK 3819 |
| Đơn sai phòng của suất, ghế sai phòng, chi tiết sai suất | Bị chặn bằng FK 1452 |
| Hai đơn CONFIRMED cùng suất/ghế | Bị chặn 1062; trạng thái CANCELLED giả khi INSERT không lách được |
| Sửa trạng thái chi tiết để giải phóng ghế trái phép | Trigger chặn 1644 |
| Hủy -> đặt lại, giữ lịch sử; cùng ghế suất khác | Thành công; dòng cũ CANCELLED/active key NULL, dòng mới CONFIRMED |
| Hủy lặp, phục hồi đơn/chi tiết cũ, sửa giá cũ/xóa lịch sử | Hủy lặp không ảnh hưởng đơn mới; hành vi sai bị chặn 1644 |
| Đổi giá suất sau tạo đơn | Giá chi tiết/tổng đơn cũ vẫn 80.000 VND |
| Hai kết nối, chủ khóa COMMIT | Kết nối thứ hai chờ khoảng 2,55 giây rồi lỗi 1062; một chủ ghế |
| Hai kết nối, chủ khóa ROLLBACK | Kết nối thứ hai chờ khoảng 3,02 giây rồi thành công |

**35 kiểm tra trong verify.sql + 3 kiểm tra bổ sung runner = 38 đạt.** Những ca SQL cố ý gây lỗi chỉ chạy trong DB thử. Fixture SQL rollback; fixture race còn trong DB thử cùng tài khoản INACTIVE không đăng nhập. AUTO_INCREMENT có thể nhảy số do INSERT lỗi/rollback, đó không phải mất dữ liệu.

**Chưa kiểm chứng vì chưa có backend:** quyền ADMIN/STAFF, tuổi tại quầy, đóng/hủy theo giờ, tính tổng từ giá chuẩn, giao dịch nhiều ghế toàn bộ hoặc không, phát hiện giá phiên bản cũ, chuyển trạng thái phim/phòng, chốt sức chứa, chống lịch trùng bằng service và API. Không tuyên bố DB đã tự bảo vệ các quy tắc này. Không có database thật được chuyển đổi/migrate.

## 8. ERD và nguồn kỹ thuật

`database-erd.mmd` là Mermaid theo đúng 11 bảng/cột của schema; FK ghép/UNIQUE ghép xem thêm tên constraint trong schema và mục 3. Ảnh ERD xuất từ cùng danh sách bảng/cột/quan hệ bằng công cụ đồ họa có sẵn, không phải ảnh thiết kế cũ. Ký hiệu UK trên nhiều cột trong Mermaid có thể thuộc **một khóa ghép**, không có nghĩa từng cột duy nhất riêng.

Tham chiếu chính thức đã dùng để xác minh lựa chọn MySQL:

- [Generated columns và hạn chế FK/CASCADE](https://dev.mysql.com/doc/refman/8.4/en/create-table-generated-columns.html)
- [CREATE INDEX và UNIQUE/NULL](https://dev.mysql.com/doc/refman/8.4/en/create-index.html)
- [InnoDB locking reads](https://dev.mysql.com/doc/refman/8.4/en/innodb-locking-reads.html)
- [Trigger syntax và giao dịch](https://dev.mysql.com/doc/refman/8.4/en/trigger-syntax.html)

## 9. Quyết định còn chờ Kha duyệt

Không sửa trạng thái duyệt trong business-rules/screens. Các lựa chọn sau là **Đề xuất chờ Kha duyệt** trước khi triển khai backend thật:

1. D-03/D-04: tuổi tối thiểu nội bộ 0–18, phim ít nhất một thể loại, tên/mã/URL; ACTIVE/INACTIVE; khóa thời lượng/tuổi và lưu snapshot suất.
2. D-05: giữ sơ đồ/loại ghế ổn định sau khi có suất; điều kiện ngừng phòng/ghế và cách chốt sức chứa D-12. Chọn capacity_snapshot khi bắt đầu như hiện tại hoặc chính sách khóa sức chứa đơn giản hơn.
3. D-06/D-07: lưu giờ Việt Nam; đóng đặt/hủy từ giờ bắt đầu; khóa sửa suất đã có lịch sử đơn; hủy suất hủy toàn bộ đơn trong một giao dịch.
4. D-08/D-09: CONFIRMED ngay, chưa giữ chỗ tạm/thông tin khách; hủy toàn bộ, không phục hồi; snapshot/history không xóa. Đồng ý 7 trigger và cột trạng thái chi tiết do DB quản lý.
5. D-10: hai giá theo suất, DECIMAL VND nguyên, VIP >= thường; tăng price_version và yêu cầu xác nhận lại khi giá đổi; tổng bất biến ngay lúc tạo đơn.
6. D-02/D-11: STAFF tra cứu toàn rạp nhưng chỉ hủy đơn mình tạo; giữ người thao tác, chỉ khóa tài khoản; chính sách ADMIN cuối cùng và cấp/đổi mật khẩu chưa triển khai.
7. Quy tắc email/username không phân biệt hoa thường/dấu, chuẩn hóa trim; UNIQUE tên thể loại và mã phim; có cho phép email quốc tế hay giới hạn email ASCII ở backend.
8. Phê duyệt tên DB `cinema_management` và tài khoản kết nối Laragon thực trước nhập dữ liệu dùng cho phát triển; bản hiện tại mới tạo DB thử riêng.

## 10. Từ điển dữ liệu khớp schema

Các bảng dưới đây được lấy từ metadata của schema đã nhập trên MySQL thử. `—` ở mặc định nghĩa không khai báo default cho cột NOT NULL; `NULL` là default nullable. `NN` = NOT NULL, `N` = cho NULL. Các khóa ghép, CHECK, trigger, quan hệ được giải thích ở trên và định nghĩa đầy đủ trong schema.sql. Không suy luận PK/UNIQUE riêng từ một thành phần khóa ghép.

### users

Tài khoản và tình trạng đăng nhập; không lưu khách hàng.

| Cột | Kiểu MySQL | NULL | Mặc định / sinh tự động | Khóa tham gia | Ý nghĩa |
|---|---|---|---|---|---|
| id | `bigint unsigned` | NN | AUTO_INCREMENT | PK | Định danh nội bộ |
| username | `varchar(50)` | NN | — | UK | Tên đăng nhập |
| email | `varchar(254)` | NN | — | UK | Email tài khoản |
| display_name | `varchar(100)` | NN | — | — | Tên hiển thị |
| password_hash | `varchar(255)` | N | NULL | — | BCrypt hash, không phải mật khẩu thuần |
| status | `varchar(16)` | NN | INACTIVE | — | Trạng thái theo CHECK của bảng |
| created_at | `datetime(6)` | NN | CURRENT_TIMESTAMP(6) | — | Thời điểm tạo theo giờ Việt Nam |
| updated_at | `datetime(6)` | NN | CURRENT_TIMESTAMP(6); DEFAULT_GENERATED on update CURRENT_TIMESTAMP(6) | — | Thời điểm cập nhật |

### roles

Hai vai trò ADMIN/STAFF.

| Cột | Kiểu MySQL | NULL | Mặc định / sinh tự động | Khóa tham gia | Ý nghĩa |
|---|---|---|---|---|---|
| id | `bigint unsigned` | NN | AUTO_INCREMENT | PK | Định danh nội bộ |
| code | `varchar(16)` | NN | — | UK | Mã định danh nghiệp vụ |
| name | `varchar(100)` | NN | — | — | Tên hiển thị |

### user_roles

Gán nhiều vai trò cho tài khoản, không lặp cặp.

| Cột | Kiểu MySQL | NULL | Mặc định / sinh tự động | Khóa tham gia | Ý nghĩa |
|---|---|---|---|---|---|
| user_id | `bigint unsigned` | NN | — | PK,FK | Tài khoản liên quan |
| role_id | `bigint unsigned` | NN | — | PK,FK | Vai trò liên quan |

### genres

Danh mục thể loại phim.

| Cột | Kiểu MySQL | NULL | Mặc định / sinh tự động | Khóa tham gia | Ý nghĩa |
|---|---|---|---|---|---|
| id | `bigint unsigned` | NN | AUTO_INCREMENT | PK | Định danh nội bộ |
| name | `varchar(100)` | NN | — | UK | Tên hiển thị |
| description | `varchar(500)` | N | NULL | — | Mô tả tùy chọn |

### movies

Thông tin phim hiện tại.

| Cột | Kiểu MySQL | NULL | Mặc định / sinh tự động | Khóa tham gia | Ý nghĩa |
|---|---|---|---|---|---|
| id | `bigint unsigned` | NN | AUTO_INCREMENT | PK | Định danh nội bộ |
| code | `varchar(32)` | NN | — | UK | Mã định danh nghiệp vụ |
| title | `varchar(200)` | NN | — | index/ghép: xem schema | Tên phim |
| description | `text` | N | NULL | — | Mô tả tùy chọn |
| duration_minutes | `smallint unsigned` | NN | — | — | Thời lượng phim hiện tại, phút |
| minimum_age | `tinyint unsigned` | NN | 0 | — | Tuổi tối thiểu theo quy ước nội bộ |
| poster_url | `varchar(2048)` | N | NULL | — | URL poster |
| trailer_url | `varchar(2048)` | N | NULL | — | URL trailer |
| status | `varchar(16)` | NN | ACTIVE | index/ghép: xem schema | Trạng thái theo CHECK của bảng |
| created_at | `datetime(6)` | NN | CURRENT_TIMESTAMP(6) | — | Thời điểm tạo theo giờ Việt Nam |
| updated_at | `datetime(6)` | NN | CURRENT_TIMESTAMP(6); DEFAULT_GENERATED on update CURRENT_TIMESTAMP(6) | — | Thời điểm cập nhật |

### movie_genres

Liên kết phim và thể loại, không lặp cặp.

| Cột | Kiểu MySQL | NULL | Mặc định / sinh tự động | Khóa tham gia | Ý nghĩa |
|---|---|---|---|---|---|
| movie_id | `bigint unsigned` | NN | — | PK,FK | Phim liên quan |
| genre_id | `bigint unsigned` | NN | — | PK,FK | Thể loại liên quan |

### rooms

Phòng chiếu trong một rạp.

| Cột | Kiểu MySQL | NULL | Mặc định / sinh tự động | Khóa tham gia | Ý nghĩa |
|---|---|---|---|---|---|
| id | `bigint unsigned` | NN | AUTO_INCREMENT | PK | Định danh nội bộ |
| code | `varchar(32)` | NN | — | UK | Mã định danh nghiệp vụ |
| name | `varchar(100)` | NN | — | — | Tên hiển thị |
| status | `varchar(16)` | NN | ACTIVE | — | Trạng thái theo CHECK của bảng |
| created_at | `datetime(6)` | NN | CURRENT_TIMESTAMP(6) | — | Thời điểm tạo theo giờ Việt Nam |

### seats

Sơ đồ vật lý và tình trạng sử dụng ghế từng phòng.

| Cột | Kiểu MySQL | NULL | Mặc định / sinh tự động | Khóa tham gia | Ý nghĩa |
|---|---|---|---|---|---|
| id | `bigint unsigned` | NN | AUTO_INCREMENT | PK | Định danh nội bộ |
| room_id | `bigint unsigned` | NN | — | FK | Phòng chiếu; ở booking là ngữ cảnh FK ghép |
| code | `varchar(16)` | NN | — | — | Mã định danh nghiệp vụ |
| row_label | `varchar(4)` | NN | — | — | Hàng ghế |
| seat_number | `smallint unsigned` | NN | — | — | Số ghế trong hàng |
| seat_type | `varchar(16)` | NN | STANDARD | — | STANDARD hoặc VIP |
| status | `varchar(16)` | NN | ACTIVE | — | Trạng thái theo CHECK của bảng |

### showtimes

Phim được xếp trong phòng và khoảng giờ, giá hiện tại.

| Cột | Kiểu MySQL | NULL | Mặc định / sinh tự động | Khóa tham gia | Ý nghĩa |
|---|---|---|---|---|---|
| id | `bigint unsigned` | NN | AUTO_INCREMENT | PK | Định danh nội bộ |
| movie_id | `bigint unsigned` | NN | — | FK | Phim liên quan |
| room_id | `bigint unsigned` | NN | — | FK | Phòng chiếu; ở booking là ngữ cảnh FK ghép |
| duration_minutes_snapshot | `smallint unsigned` | NN | — | — | Thời lượng áp dụng cho suất |
| minimum_age_snapshot | `tinyint unsigned` | NN | — | — | Tuổi áp dụng cho suất |
| starts_at | `datetime` | NN | — | index/ghép: xem schema | Bắt đầu theo giờ Việt Nam |
| ends_at | `datetime` | NN | — | — | Kết thúc phim, chưa cộng nghỉ |
| standard_price | `decimal(12,0)` | NN | — | — | Đơn giá ghế thường, VND |
| vip_price | `decimal(12,0)` | NN | — | — | Đơn giá ghế VIP, VND |
| price_version | `bigint unsigned` | NN | 0 | — | Phiên bản giá do service tăng |
| capacity_snapshot | `smallint unsigned` | N | NULL | — | Sức chứa sử dụng chốt cho suất |
| status | `varchar(16)` | NN | SCHEDULED | — | Trạng thái theo CHECK của bảng |
| cancelled_at | `datetime(6)` | N | NULL | — | Thời điểm hủy |
| cancelled_by | `bigint unsigned` | N | NULL | FK | Tài khoản thực hiện hủy |
| cancel_reason | `varchar(500)` | N | NULL | — | Lý do hủy |
| created_at | `datetime(6)` | NN | CURRENT_TIMESTAMP(6) | — | Thời điểm tạo theo giờ Việt Nam |

### bookings

Phiếu đặt chỗ, giá và lịch sử hủy.

| Cột | Kiểu MySQL | NULL | Mặc định / sinh tự động | Khóa tham gia | Ý nghĩa |
|---|---|---|---|---|---|
| id | `bigint unsigned` | NN | AUTO_INCREMENT | PK | Định danh nội bộ |
| code | `varchar(40)` | NN | — | UK | Mã định danh nghiệp vụ |
| showtime_id | `bigint unsigned` | NN | — | FK | Suất chiếu của phiếu/chi tiết |
| room_id | `bigint unsigned` | NN | — | FK | Phòng chiếu; ở booking là ngữ cảnh FK ghép |
| created_by | `bigint unsigned` | NN | — | FK | Người tạo phiếu |
| created_by_name_snapshot | `varchar(100)` | NN | — | — | Tên người tạo lúc lập phiếu |
| movie_title_snapshot | `varchar(200)` | NN | — | — | Tên phim lúc lập phiếu |
| room_code_snapshot | `varchar(32)` | NN | — | — | Mã phòng lúc lập phiếu |
| price_version_snapshot | `bigint unsigned` | NN | — | — | Phiên bản giá lúc lập phiếu |
| total_amount | `decimal(14,0)` | NN | — | — | Tổng giá trị đặt chỗ đã chốt |
| age_checked | `tinyint(1)` | NN | 0 | — | Nhân viên xác nhận kiểm tra tuổi |
| status | `varchar(16)` | NN | CONFIRMED | — | Trạng thái theo CHECK của bảng |
| created_at | `datetime(6)` | NN | CURRENT_TIMESTAMP(6) | — | Thời điểm tạo theo giờ Việt Nam |
| cancelled_at | `datetime(6)` | N | NULL | — | Thời điểm hủy |
| cancelled_by | `bigint unsigned` | N | NULL | FK | Tài khoản thực hiện hủy |
| cancel_reason | `varchar(500)` | N | NULL | — | Lý do hủy |

### booking_seats

Ghế trong phiếu, giá chụp và khóa chiếm ghế còn hiệu lực.

| Cột | Kiểu MySQL | NULL | Mặc định / sinh tự động | Khóa tham gia | Ý nghĩa |
|---|---|---|---|---|---|
| id | `bigint unsigned` | NN | AUTO_INCREMENT | PK | Định danh nội bộ |
| booking_id | `bigint unsigned` | NN | — | FK | Phiếu đặt chỗ cha |
| showtime_id | `bigint unsigned` | NN | — | FK | Suất chiếu của phiếu/chi tiết |
| room_id | `bigint unsigned` | NN | — | FK | Phòng chiếu; ở booking là ngữ cảnh FK ghép |
| seat_id | `bigint unsigned` | NN | — | FK | Ghế vật lý liên quan |
| seat_code_snapshot | `varchar(16)` | NN | — | — | Mã ghế lúc lập phiếu |
| seat_type_snapshot | `varchar(16)` | NN | — | — | Loại ghế lúc lập phiếu |
| unit_price | `decimal(12,0)` | NN | — | — | Đơn giá ghế lúc lập phiếu |
| booking_status | `varchar(16)` | NN | CONFIRMED | — | Trạng thái sao chép từ cha bằng trigger |
| active_seat_id | `bigint unsigned` | N | GENERATED: (case when (`booking_status` = _utf8mb4\'CONFIRMED\') then `seat_id` else NULL end) | — | Khóa ghế hiệu lực sinh từ trạng thái |


## 10. Nâng cấp V001 — đặt chỗ và idempotency (28/10/2026)

Baseline schema.sql/seed.sql **giữ nguyên**. Backend đặt chỗ cần chạy một lần `database/migrations/V001__booking_idempotency.sql` sau baseline, trên đúng DB được chọn. Không tự chạy SQL lúc khởi động; Hibernate validate kiểm tra mapping mới. Với DB đã có, kiểm tra SHOW COLUMNS/SHOW INDEX/SHOW TRIGGERS trước khi chạy, không chạy lại baseline hoặc V001. MySQL DDL có implicit commit: nếu migration lỗi giữa chừng, kiểm tra trạng thái thực tế và lập sửa tiếp có kiểm soát, không --force/reset.

| Cột mới bookings | Kiểu | NULL/default | Ràng buộc/mục đích |
|---|---|---|---|
| idempotency_key | VARCHAR(36) ASCII ascii_bin | NULL mặc định | UUID lowercase từ service; UNIQUE(created_by,idempotency_key), scope theo tài khoản |
| request_hash | VARCHAR(64) ASCII ascii_bin | NULL mặc định | SHA-256 canonical, bất biến; không trả API |

CHECK yêu cầu cả hai null (legacy), hoặc cả hai có đủ độ dài36/64. Chỉ service chuẩn hóa UUID/hash; CHECK không tự chứng minh cú pháp UUID/SHA. Trigger mới bu_bookings_request chặn thay key/hash khi UPDATE, không ảnh hưởng cancel các record legacy có null. Không backfill hoặc sửa người tạo/snapshot/lịch sử cũ. Số bảng vẫn11, trigger sau V001 là8; UNIQUE ghế active và trigger cancel trước đây giữ nguyên. MMD đã thêm hai cột; PNG đã xuất ở bước baseline chỉ mô tả baseline, chưa render lại metadata mới.

Khởi tạo mới: schema.sql → seed.sql → migrations/V001__booking_idempotency.sql. Nâng cấp hiện có: chỉ V001 sau khi đối chiếu baseline. Không áp dụng vào DB shop hoặc DB khác để thử. Bước này chỉ đã áp dụng trên DB test mới, chưa chạy vào DB sử dụng thực tế của Kha.

Booking ghi READ COMMITTED theo khóa phim→phòng→suất→booking→ghế (ghế ID tăng). Map đọc REPEATABLE READ cho một snapshot; không giữ chỗ. Tạo + chi tiết toàn vẹn, tổng/giá từ server; cancel chỉ UPDATE cha và trigger theo booking_id. BookingTransactionService là lớp transaction, BookingService là facade xử lý va chạm UNIQUE idempotency **sau rollback** rồi đọc kết quả đã commit. Không bắt lỗi và tiếp tục transaction đã hỏng. Hai request cùng key/nội dung replay cùng booking, khác nội dung409; khác actor có namespace key khác.

Test nâng cấp có thể tái lập bằng Python chuẩn/mysql CLI:

```text
python database/test_booking_migration.py --mysql <duong-dan-mysql.exe> --host 127.0.0.1 --port 3306 --user <tai-khoan-SQL>
```

Runner chỉ tạo DB mới có prefix cinema_management_test_, nhập baseline/seed, tạo booking legacy trước migration, áp dụng V001, xác nhận lịch sử/giá/ghế còn nguyên và hủy legacy vẫn giải phóng slot; giữ fixture theo trigger, không DROP/reset. Mật khẩu SQL nếu cần lấy từ môi trường MYSQL_PWD, không lưu/hiện trong lệnh hoặc source. Kết quả đã chạy: DB `cinema_management_test_booking_upgrade_20261028_100415`, legacyHistoryPreserved=true, legacyCancellationWorks=true.

DB tích hợp mới: `cinema_management_test_bookings_20261028_095639`, MySQL8.4.3/3306, đã nhập baseline/seed/V001. Dữ liệu thử booking/history được giữ theo trigger; tài khoản test không còn dùng đăng nhập sau suite. Không dữ liệu thật bị thay đổi.

# Hướng dẫn sử dụng frontend quản lý rạp phim

Ngày bàn giao: 28/10/2026. Đạt triển khai frontend; Kha phụ trách backend, CSDL và hợp đồng API.

Giao diện dùng API thật cùng origin, không có chế độ mock trong mã chạy ứng dụng. Phần quản lý tài khoản đang chờ API của Kha và chưa thể tạo, khóa hoặc đổi vai trò qua giao diện.

## Khởi động

1. Bật MySQL trong Laragon. Database local đã chuẩn bị là `cinema_management`; không nhập lại schema/seed/migration trên database đang có dữ liệu.
2. Mở PowerShell trong thư mục `cinema-management` có `pom.xml`.
3. Khai báo các biến `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` theo README. Các biến chỉ có hiệu lực trong phiên PowerShell đó. Không ghi mật khẩu vào Git.
4. Chạy `./mvnw.cmd package`, sau đó `java -jar target/cinema-management-0.0.1-SNAPSHOT.jar`.
5. Đợi `Started CinemaApplication`. Mở `http://127.0.0.1:8080/api/health`, phải nhận `{"status":"UP","database":"UP"}`.
6. Mở `http://127.0.0.1:8080/` hoặc `/login.html`. ADMIN vào tổng quan; STAFF vào lịch chiếu.

Tài khoản đăng nhập được cấp riêng. Không có mật khẩu mặc định. Nếu cần tạo tài khoản local mới, dùng profile `local-accounts` theo README mục 8, không chèn mật khẩu thuần vào SQL. Bản local đã có ADMIN và STAFF để nghiệm thu; credential không được lưu trong hướng dẫn hoặc ảnh.

Cổng 4174 của lượt demo cũ đã dừng; toàn bộ hướng dẫn này dùng cổng 8080. Nhấn Ctrl+C tại tiến trình Java để dừng backend. Sau khi tắt máy, cần bật MySQL và chạy Java lại; dữ liệu đã lưu trong MySQL vẫn còn, phiên đăng nhập cũ sẽ hết hiệu lực.

## Màn hình và quyền

| Màn hình | Đường dẫn | Quyền và thao tác |
|---|---|---|
| Đăng nhập | `/login.html` | Email/mật khẩu, validation, CSRF, cookie phiên |
| Tổng quan | `/admin/dashboard.html` | ADMIN; tổng hợp đủ các trang API theo ngày |
| Thể loại | `/admin/genres.html` | ADMIN; tìm kiếm, phân trang, thêm/sửa/xóa |
| Phim | `/admin/movies.html` | ADMIN; tìm/lọc/sắp xếp/phân trang, thêm/sửa/xóa, nhiều thể loại |
| Phòng | `/admin/rooms.html` | ADMIN; tìm/lọc/phân trang, thêm/sửa/xóa, mở sơ đồ |
| Sơ đồ ghế | `/admin/seats.html` | ADMIN; chọn phòng, thêm/sửa/xóa ghế, bulk cho phòng rỗng |
| Lịch chiếu | `/admin/showtimes.html` | ADMIN quản lý; STAFF chỉ đọc và chuyển tới đặt ghế |
| Đặt chỗ và tra cứu | `/admin/bookings.html` | ADMIN/STAFF đặt, tra cứu; chỉ ADMIN được hủy khi hợp lệ |
| Thống kê | `/admin/statistics.html` | ADMIN; 4 báo cáo, ngày/phim/phòng, phân trang |
| Tài khoản | `/admin/users.html` | ADMIN; thông báo chờ API, chưa có thao tác ghi |

Menu chỉ hiển thị theo vai trò; backend vẫn là nơi quyết định quyền. Trên điện thoại, bấm Menu; Tab/Enter dùng được, Escape đóng menu. Trong hộp thoại, Tab/Shift+Tab luân chuyển giữa các ô, Escape đóng khi không gửi dữ liệu. Liên kết “Bỏ qua điều hướng” đưa focus vào nội dung.

## Chuẩn bị danh mục và phòng

1. Vào Thể loại, thêm tên và mô tả. Tên trùng theo collation MySQL có thể bị từ chối dù khác dấu/hoa thường.
2. Vào Phim, nhập mã duy nhất, tên, phút, tuổi 0–18, trạng thái, chọn ít nhất một thể loại. Poster/trailer phải là URL HTTP/HTTPS tuyệt đối; có thể bỏ trống. Ảnh lỗi có phần thay thế.
3. Vào Phòng chiếu, tạo mã/tên phòng, chọn trạng thái. Hai số đếm là ghế vật lý/ghế đang sử dụng, không phải ghế trống của suất.
4. Mở Sơ đồ ghế. Phòng rỗng có thể tạo hàng loạt: hàng `A,B,C`, số ghế mỗi hàng và loại ban đầu. Không có reset/ghi đè sơ đồ.
5. Chọn từng ghế để sửa hàng/số/loại/trạng thái, hoặc xác nhận xóa khi backend cho phép. Thường, VIP và ngừng sử dụng có nhãn, không chỉ dựa vào màu.

Phim/phòng/ghế đã có lịch sử chịu các khóa nghiệp vụ. Không xóa lịch sử hoặc tự hủy suất/đơn để vượt ràng buộc. Ngừng phim/phòng/ghế đang hoạt động có bước xác nhận.

## Lập lịch

1. ADMIN vào Lịch chiếu, chọn Tạo suất chiếu, phim/phòng đang sử dụng, thời điểm bắt đầu giờ Việt Nam và hai mức giá VND nguyên.
2. Giao diện chỉ hiển thị giờ kết thúc dự kiến. Backend chụp thời lượng/tuổi, tính giờ kết thúc và kiểm tra ít nhất 15 phút nghỉ.
3. Nếu trùng lịch, form giữ dữ liệu và hiển thị `SHOWTIME_CONFLICT`; chọn lại giờ/phòng. Không tự đổi lịch xung đột.
4. Lọc theo ngày bắt đầu suất, phim, phòng, trạng thái. Suất qua nửa đêm hiển thị cả ngày và giờ kết thúc.
5. Mở Chi tiết. Các nút sửa/hủy/xóa chỉ xuất hiện khi backend cấp liên kết HATEOAS tương ứng. Hủy yêu cầu lý do và xác nhận. Suất có bất kỳ lịch sử booking hiện không được sửa/hủy/xóa.

## Đặt chỗ và xử lý mất mạng

1. Chọn ngày → suất → xem sơ đồ. Có thể đi từ nút Đặt ghế trong Lịch chiếu.
2. Chọn tối đa 20 ghế trống, xem mã/loại/giá và tổng dự kiến. Ghế đang chọn chưa được giữ cho khách.
3. Nếu tuổi tối thiểu lớn hơn 0, nhân viên phải tích xác nhận đã kiểm tra tuổi tại quầy.
4. Bấm Xem lại và xác nhận, kiểm tra thông tin rồi xác nhận. Kết quả hiển thị mã đặt chỗ, trạng thái và giá trị chính thức từ server, không gọi là đã thanh toán.
5. Khi mất phản hồi, giữ nguyên ý định đặt. Giao diện lưu UUID/body trong sessionStorage theo tài khoản và khóa đổi suất/ghế. Bấm “Kiểm tra kết quả / gửi lại cùng mã yêu cầu” để tra lại hoặc gửi lại đúng key/body. Tải lại trang trong cùng tab vẫn giữ ý định đang chờ xác minh.
6. Khi backend trả lỗi xác định 409, sơ đồ/giá được tải lại, loại bỏ ghế không còn trống và bỏ xác nhận tuổi. Hộp xem lại cũ đóng; nhân viên phải kiểm tra lại và xác nhận một yêu cầu mới. Không tự gửi lại.

Không xóa trạng thái chờ xác minh rồi tạo đơn khác cho cùng ý định. SessionStorage chỉ giữ dữ liệu ý định đặt, không lưu mật khẩu, cookie phiên hay CSRF. Mất tab/trình duyệt có thể mất ý định cục bộ; tra cứu danh sách/mã trước khi đặt lại.

## Tra cứu và hủy

Tìm chứa mã qua bộ lọc danh sách hoặc nhập mã đầy đủ và bấm Tra cứu đúng mã. Có bộ lọc ID suất và trạng thái. Chi tiết dùng snapshot tên/ghế/giá/nhân viên của đơn; không tính lại theo danh mục mới.

ADMIN có thể hủy đơn còn hiệu lực trước giờ chiếu khi backend cho phép, cần nhập lý do và xác nhận. Sau hủy, backend giữ lịch sử và giải phóng ghế. STAFF không được hủy, kể cả đơn mình tạo. Không có sửa ghế/giá hoặc xóa đơn. Chưa có thu tiền, hoàn tiền hay vé online.

## Xem báo cáo

Chọn khoảng từ/đến, bao gồm hai đầu, tối đa 31 ngày; có thể lọc phim/phòng. Chọn một trong bốn báo cáo:

- Suất chiếu theo ngày bắt đầu.
- Đơn/ghế/giá trị đặt chỗ theo phim trên suất chưa hủy.
- Lấp đầy theo suất, ghi cơ sở sức chứa tạm tính/snapshot/thiếu snapshot.
- Đơn/ghế/giá trị hủy theo ngày hủy.

Giá trị đặt chỗ không phải doanh thu đã thu; giá trị hủy không phải tiền đã hoàn. Null sức chứa/tỷ lệ không biến thành 0%. Tỷ lệ trên 100% được giữ và có cảnh báo. Tổng quan tổng hợp đủ các trang dữ liệu của ngày; bảng phim được sắp theo số ghế đã đặt, không dùng số liệu giả.

## Chạy kiểm thử và demo

Xem [báo cáo nghiệm thu](BAO_CAO.md), [bằng chứng E2E](evidence/frontend-e2e.json), [tình huống biên](evidence/frontend-edge.json), [CRUD bổ sung](evidence/frontend-crud.json) và [bố cục](evidence/frontend-visual.json).

Các script trong `tests/` dùng Node.js và Playwright/Chrome, backend local đang chạy và tài khoản ADMIN/STAFF riêng. Không thêm thư viện này vào backend. Tạo tệp JSON tạm ngoài repo có `adminEmail`, `adminPassword`, `staffEmail`, `staffPassword`; đặt đường dẫn vào biến môi trường `CINEMA_CREDENTIALS_FILE`. Runner xóa tệp ngay sau khi đọc. Có thể truyền qua stdin cho `frontend-e2e.cjs`.

```powershell
# NODE_PATH phải trỏ tới thư mục node_modules có playwright của máy bạn.
# CHROME_PATH chỉ cần đặt nếu Chrome ở đường dẫn khác mặc định của script.
$env:CINEMA_BASE_URL = 'http://127.0.0.1:8080'
$env:CINEMA_CREDENTIALS_FILE = '<duong-dan-tep-tai-khoan-tam-ngoai-repo>'
node docs/frontend/tests/frontend-e2e.cjs
```

Chuẩn bị lại tệp tạm trước mỗi lần chạy `frontend-edge.cjs`, `frontend-crud.cjs`, `frontend-visual.cjs`. Các script dùng fixture từ báo cáo E2E trước đó. Kiểm thử chỉ dùng DB phát triển local. Tên/mã fixture có tiền tố UI/TEMP/MORE; fixture chưa có lịch sử có thể xóa theo API, fixture đã có suất/booking phải giữ theo chính sách backend. Không xóa seed hoặc reset database để demo.

Kịch bản demo: ADMIN đăng nhập → tạo thể loại/phim/phòng → tạo ghế → lập suất tương lai → STAFF đăng nhập đặt 2 ghế → tra mã → ADMIN hủy → kiểm tra ghế trống → STAFF đặt lại → ADMIN xem báo cáo. Thử thêm trùng lịch, xóa có liên kết và mất phản hồi cùng key. Không nhập lại mật khẩu trong báo cáo/ảnh/Git.

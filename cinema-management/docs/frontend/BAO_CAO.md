# Báo cáo triển khai và nghiệm thu frontend

Ngày: 28/10/2026. Chủ đề: hệ thống quản lý rạp chiếu phim của Kha và Đạt. Đạt phụ trách frontend. Mục đích báo cáo là đối chiếu phần việc trong tài liệu phân công, mô tả triển khai, bằng chứng kiểm thử và phần còn phụ thuộc backend.

## Kết quả và giới hạn

Đã triển khai frontend sử dụng API thật cho đăng nhập/đăng xuất, tổng quan, thể loại, phim, phòng, sơ đồ ghế, lịch chiếu, đặt/tra cứu/hủy chỗ và bốn báo cáo. Không sửa Java, SQL, pom.xml, cấu hình backend hoặc hợp đồng API. Không tìm thấy AGENTS.md trong repo. Tiếp tục làm trên nhánh `codex/frontend-movies` đã chứa giao diện phim từ lượt trước; bàn giao lên frontend/dat và main theo yêu cầu của Đạt.

Quản lý tài khoản chưa thể hoàn thành nghiệp vụ do backend chưa có controller và hợp đồng CRUD tài khoản. Màn hình giải thích rõ phần chờ Kha, không có thao tác ghi giả. Chưa thể tuyên bố toàn bộ đồ án hoàn thành cho tới khi phần này được bàn giao và các quyết định nghiệp vụ đang chờ được nhóm chốt.

## Đối chiếu tài liệu phân công

| Nhóm việc | Kết quả |
|---|---|
| Màn hình và luồng | Bảng route/vai trò trong HUONG_DAN.md; ADMIN vào tổng quan, STAFF vào lịch |
| Thành phần chung | Sidebar, tài khoản, form/dialog, thông báo, loading/empty/error, xác nhận, phân trang |
| API dùng chung | Cookie same-origin, CSRF trong bộ nhớ, lỗi status/code/fieldErrors, không log credential |
| Xác thực | Đăng nhập/đăng xuất thật, nhận phiên sau tải lại, phân vai trò, xử lý 401 trong ứng dụng |
| Thể loại và phim | CRUD thật, tìm/lọc/phân trang, đầy đủ JSON, nhiều thể loại, bảo vệ liên kết |
| Phòng và ghế | CRUD phòng/ghế, sơ đồ theo phòng, bulk phòng rỗng, nhãn loại/trạng thái |
| Lịch | Ngày/phim/phòng, giờ VN, kết thúc dự kiến, CRUD/hủy theo HATEOAS, lỗi trùng lịch giữ form |
| Đặt chỗ | Chọn ghế, giá server, xác nhận tuổi, idempotency/retry, tra cứu, ADMIN hủy |
| Tổng quan và thống kê | Dữ liệu thật của 4 báo cáo, phân trang, null/tỷ lệ trên 100% và cơ sở thời gian rõ ràng |
| Kiểm thử và báo cáo | 4 bộ kiểm tra frontend, ảnh chụp, hướng dẫn vận hành/demo; tài khoản chờ Kha |

## Kiến trúc giao diện

`api.js` gửi JSON/cookie/CSRF và chuẩn hóa lỗi mạng/HTTP. `auth.js` quản lý phiên trong bộ nhớ và chuyển trang theo vai trò. `layout.js` dựng menu theo quyền, tài khoản và menu điện thoại. `ui.js` tái sử dụng bảng, phân trang, bộ lọc, dialog, focus và lỗi từng trường. `catalog.js` dùng chung cho thể loại/phòng; các module phim, ghế, lịch, booking và thống kê dùng chung nền tảng này.

Mã không dùng innerHTML để hiển thị dữ liệu server. URL ảnh được kiểm tra HTTP/HTTPS; lỗi ảnh có phần thay thế. Ngày giờ suất giữ quy ước +07:00; frontend gửi đủ trường PUT và không gửi Entity, giá tự tính hoặc giờ kết thúc để quyết định nghiệp vụ. `_links` chỉ quyết định nút nào được hiển thị; backend vẫn xác thực khi ghi.

Yêu cầu đặt chỗ có UUID riêng, giữ nguyên body và key khi chưa biết kết quả. Response 409 xác định sẽ tải lại sơ đồ, loại ghế không còn trống, bỏ xác nhận tuổi và đóng hộp xem lại cũ để xác nhận lại. Không tự hủy/xóa dữ liệu lịch sử hoặc tự retry thao tác ghi khác.

## Kiểm thử trên máy Đạt

Môi trường local: Windows, Chrome headless, MySQL Laragon 8.4.3, database `cinema_management`, Spring Boot 3.5.16, Java 26 đang có trên máy, bytecode release 21. Ứng dụng local khởi động và health/DB UP; chưa kết luận Java 26 là phiên bản hỗ trợ chính thức hoặc đã kiểm chứng runtime JDK 21/HTTPS/tải lớn.

| Bộ kiểm tra | Kết quả | Bằng chứng |
|---|---|---|
| E2E qua UI + API/MySQL thật | 35 kiểm tra đạt | evidence/frontend-e2e.json |
| Tình huống biên | 15 kiểm tra đạt | evidence/frontend-edge.json |
| CRUD bổ sung | 11 kiểm tra đạt | evidence/frontend-crud.json |
| 9 màn hình × 3 kích thước | 27 kiểm tra không tràn ngang; 18 ảnh | evidence/frontend-visual.json |

Đây là 88 điều kiện kiểm tra frontend, không phải số test backend của Kha. Không cộng hoặc thay thế báo cáo backend. Các lần chạy cuối của từng bộ không ghi nhận lỗi JavaScript. Tên fixture/ID trong JSON chứng minh dữ liệu được tạo và đọc lại từ API, không phải bộ mock runtime.

Đã đóng gói JAR thành công và khởi động lại từ tài nguyên classpath thông thường (không phụ thuộc thư mục static bên ngoài). Kiểm tra thêm đăng nhập/tải 9 màn hình và ba kích thước từ JAR đạt; health trả `{"status":"UP","database":"UP"}`. Bằng chứng riêng ở `evidence/packaged-smoke.json`; không ghi đè báo cáo E2E đầy đủ.

E2E bao gồm ADMIN login, CRUD danh mục/phòng, bulk ghế, đổi VIP, lập suất, lỗi trùng lịch, đặt STANDARD+VIP bằng giá server, reload vẫn có đơn, hủy và giải phóng ghế, bốn báo cáo, logout, STAFF login/đặt lại/không có nút hủy. CRUD bổ sung có xóa thể loại đang dùng bị chặn, PUT phim giữ thể loại, xóa tài nguyên chưa có lịch sử, sửa/hủy/xóa suất và hiển thị tên chứa HTML bằng text.

Kiểm tra mất phản hồi dùng backend thật để tạo booking thành công, sau đó chủ động làm mất response ở trình duyệt. Gửi lại cùng key nhận đúng mã/ID ban đầu và không có đơn trùng còn hiệu lực. Các ca PRICE_CHANGED và null/150% occupancy dùng response mô phỏng có kiểm soát trong script kiểm thử để tạo tình huống biên, không được thêm vào mã ứng dụng hoặc đưa vào số liệu demo. Không tuyên bố những response mô phỏng này là dữ liệu DB thực tế.

Kiểm tra bàn phím gồm liên kết bỏ qua điều hướng, Tab/Shift+Tab trong dialog, Escape và khôi phục focus, focus menu điện thoại; không dùng chỉ màu để biểu diễn ghế. 27 kiểm tra kích thước bao phủ 360/768/1366 px. Chưa làm kiểm toán WCAG đầy đủ bằng trình đọc màn hình hoặc kiểm thử chạm trên thiết bị vật lý.

## Ảnh minh chứng

- [Tổng quan](evidence/dashboard-1366.png)
- [Thể loại](evidence/genres-1366.png)
- [Phim](evidence/movies-1366.png)
- [Phòng](evidence/rooms-1366.png)
- [Sơ đồ ghế](evidence/seats-1366.png)
- [Lịch chiếu](evidence/06-showtimes.png)
- [Kết quả đặt chỗ](evidence/07-booking.png)
- [STAFF](evidence/09-staff-booking.png)
- [Gửi lại cùng yêu cầu](evidence/11-replayed-booking.png)
- [Thống kê](evidence/statistics-1366.png)
- [Điện thoại](evidence/bookings-360.png)

Ảnh không chứa mật khẩu, Cookie, CSRF hoặc Idempotency-Key. Dữ liệu có tên UI/TEMP/MORE là fixture thử, seed là minh họa đồ án; không diễn giải thành số liệu kinh doanh thật.

## Phần cần Kha hỗ trợ

1. **CRUD tài khoản chưa có API.** Request thực tế có phiên ADMIN: `GET /api/admin/users?page=0&size=20`. Response HTTP 404:

```json
{
  "status": 404,
  "code": "RESOURCE_NOT_FOUND",
  "message": "Không tìm thấy tài nguyên.",
  "path": "/api/admin/users",
  "fieldErrors": {}
}
```

Timestamp đầy đủ được lưu trong `frontend-e2e.json`. Cần Kha thống nhất các route/method/body/response cho danh sách/tìm kiếm/phân trang, tạo, khóa/mở, cấp quyền, quy tắc ADMIN cuối cùng và tác động tới phiên. Frontend chưa gửi POST/PUT/DELETE users.

2. **Trang HTML bảo vệ khi không còn phiên.** Trong ứng dụng đang mở, 401 từ API được xử lý về login. Nếu tải trực tiếp một URL `/admin/*.html` sau khi phiên hết hạn, Security có thể chặn trước khi JavaScript được tải và trả JSON 401. Đường vào hỗ trợ hiện tại là `/` hoặc `/login.html`. Nếu muốn tải trực tiếp URL bảo vệ tự chuyển login, cần Kha thống nhất xử lý request HTML tại backend; frontend không thể chạy script khi HTML chưa được cấp.

3. **Chốt chính sách còn đề xuất.** STAFF hủy, giới hạn/tuổi/giá, sức chứa snapshot và quy tắc khóa tài nguyên theo lịch sử tiếp tục theo docs/api.md hiện hành. Không tự phê duyệt các D-* từ màn hình. Sức chứa lịch sử thiếu vẫn hiển thị thiếu, không backfill.

4. **Nghiệm thu vận hành.** Nhóm bổ sung họ tên/MSSV/lớp/giảng viên vào báo cáo môn học hiện có; chạy demo trực tiếp và kiểm tra trên thiết bị/phiên bản JDK nhóm chọn. Chưa triển khai thanh toán, khách tự đặt hoặc quản trị tài khoản backend ngoài hợp đồng.

## Tệp và cách chạy lại

Xem [danh sách thay đổi](THAY_DOI.md) và [hướng dẫn](HUONG_DAN.md). Mã chạy ứng dụng ở `src/main/resources/static/`; tài liệu, ảnh và script kiểm thử ở `docs/frontend/`. Không có credential trong các tệp này. Bàn giao lên nhánh frontend/dat và main theo yêu cầu của Đạt.

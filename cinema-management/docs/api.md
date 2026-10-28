# Hợp đồng API backend

Cập nhật 27/10/2026. Base URL local: `http://127.0.0.1:8080`. Đã có health, bốn API xác thực, CRUD thể loại/phim và phòng/ghế. Đăng nhập bằng **email + mật khẩu JSON**, phiên cookie Spring Security; không có JWT, đăng ký công khai hoặc quên mật khẩu. Đã có lịch chiếu ở mục 8 và đặt/hủy tại quầy ở mục 9.

## 1. Cookie và CSRF — trình tự tích hợp cho Đạt

1. `GET /api/auth/csrf`, giữ cookie `JSESSIONID` và đọc `headerName`, `token` trong JSON.
2. `POST /api/auth/login` với cookie, `Content-Type: application/json` và header có tên/giá trị vừa nhận.
3. Đăng nhập thành công đổi session ID và xóa CSRF cũ. Gọi lại `GET /api/auth/csrf`, giữ cookie mới và token mới trước các thao tác ghi.
4. `GET /api/auth/me` dùng cookie, không cần CSRF. Mọi POST/PUT/PATCH/DELETE phải có CSRF, kể cả login/logout.
5. `POST /api/auth/logout` với cookie/token hiện tại. Bỏ tài khoản/token khỏi bộ nhớ giao diện; lấy CSRF mới trước lần đăng nhập tiếp theo.

Cookie `JSESSIONID`: **HttpOnly, SameSite=Lax, Path=/**, hết phiên server sau **30 phút không hoạt động**. HTTP localhost mặc định `Secure=false`; triển khai HTTPS đặt `SESSION_COOKIE_SECURE=true`. Trình duyệt tự quản lý cookie, JavaScript không đọc cookie phiên. Phiên lưu trong bộ nhớ server, khởi động lại app cần đăng nhập lại.

Frontend cùng origin dùng URL tương đối, `fetch` với `credentials: 'same-origin'` (hoặc `include`), `Accept: application/json`. Giữ CSRF trong bộ nhớ, gửi qua `X-CSRF-TOKEN`; không đặt mật khẩu/token trong URL hoặc log. Không gửi Bearer token, không bật CORS rộng. Nếu sau này tách origin phải thống nhất origin và chính sách cookie cụ thể trước.

Token response được Spring Security che bằng XOR; chuỗi có thể thay đổi giữa các lần GET. Dùng nguyên giá trị trả về, đi cùng đúng phiên. Phân quyền ở backend, việc frontend ẩn nút không thay thế kiểm tra quyền.

## 2. Endpoint đã hoạt động

| Method | Đường dẫn | Xác thực | CSRF | Thành công |
|---|---|---|---|---|
| GET | `/api/health` | Công khai | Không | 200 |
| GET | `/api/auth/csrf` | Công khai | Không | 200 |
| POST | `/api/auth/login` | Công khai | Có | 200 |
| GET | `/api/auth/me` | ADMIN hoặc STAFF | Không | 200 |
| POST | `/api/auth/logout` | Phiên hiện tại; cũng cho gọi khi chưa đăng nhập | Có | 200 |

### GET /api/health

Truy vấn thật cố định `SELECT COUNT(*) FROM roles`; không nhận SQL từ client. 200:

```json
{"status":"UP","database":"UP"}
```

Mất kết nối DB sau startup trả 503 `DATABASE_UNAVAILABLE`. DB/schema sai ngay startup làm Hibernate validate dừng app; chưa có listener HTTP để trả 503. Không trả thông tin kết nối hay số liệu nội bộ.

### GET /api/auth/csrf

Không có body. Tạo phiên nếu chưa có. 200:

```json
{"headerName":"X-CSRF-TOKEN","token":"<gia-tri-server-sinh>"}
```

Giữ cả cookie `Set-Cookie`. Token đơn lẻ không đủ để đăng nhập.

### POST /api/auth/login

Header `Content-Type: application/json`, `X-CSRF-TOKEN: <token>`, cookie do trình duyệt tự gửi. Body (placeholder, không phải tài khoản dùng chung):

```json
{"email":"<email-local-cua-ban>","password":"<mat-khau-rieng-cua-ban>"}
```

Email bắt buộc, đúng định dạng, tối đa 254 ký tự; tra cứu không phân biệt hoa/thường theo collation hiện tại. Mật khẩu bắt buộc, không toàn khoảng trắng, tối đa 72 ký tự **và 72 byte UTF-8** để tránh BCrypt cắt ngầm. Không trim hoặc đổi hoa/thường mật khẩu. Tạo tài khoản local yêu cầu mạnh hơn: 12–72 byte UTF-8.

200 trả cùng DTO với `/me`, đồng thời thay session ID. Ví dụ minh họa:

```json
{"id":1,"username":"kha-local","displayName":"Kha","email":"kha@example.invalid","roles":["ADMIN"]}
```

`roles` là mảng mã ADMIN/STAFF, không có tiền tố ROLE_. Chỉ tài khoản ACTIVE, có BCrypt hash và vai trò hợp lệ mới đăng nhập được. INACTIVE, sai mật khẩu và email không tồn tại đều trả **401 INVALID_CREDENTIALS**, cùng message `Thông tin đăng nhập không hợp lệ.`. Không tiết lộ nguyên nhân/email tồn tại. Đăng nhập thất bại xóa xác thực cũ nếu trước đó đã đăng nhập; lỗi validation/CSRF bị chặn trước bước này.

400 VALIDATION_FAILED: thiếu/sai email, thiếu/quá dài mật khẩu; fieldErrors theo trường. JSON hỏng: 400 INVALID_REQUEST. Content-Type không hỗ trợ: 415 sau khi qua CSRF. Thiếu/sai CSRF: 403 trước khi kiểm tra tài khoản.

### GET /api/auth/me

200 gồm đúng năm trường `id`, `username`, `displayName`, `email`, `roles` như trên. Không có phiên xác thực: 401 UNAUTHENTICATED. Không trả Entity, mật khẩu, hash hoặc session ID.

### POST /api/auth/logout

Không cần body, bắt buộc CSRF hiện tại. 200:

```json
{"status":"LOGGED_OUT"}
```

Xóa xác thực, vô hiệu hóa phiên server và gửi cookie hết hạn. Cookie phiên cũ không truy cập được `/me`. Chưa đăng nhập nhưng có phiên/token CSRF hợp lệ cũng nhận 200 (idempotent). Thiếu/sai CSRF: 403, phiên đang có vẫn giữ nguyên. **GET không đăng xuất**: hiện 401 khi ẩn danh, 403 khi đã đăng nhập theo deny mặc định.

## 3. Ma trận quyền quản trị và module tiếp theo

Movies/genres đã triển khai ở mục 6; rooms và ghế lồng trong rooms ở mục 7. Lịch chiếu đã có ở mục 8. Route phẳng `/api/admin/seats` và các module users/statistics chỉ có quy tắc Security dành trước; bookings đã triển khai ở mục 9. `{id}` là đúng một đoạn đường dẫn. Qua filter nhưng module chưa làm sẽ trả 404.

| Method | Đường dẫn cụ thể | Vai trò |
|---|---|---|
| GET | `/api/admin/movies`, `/api/admin/movies/{id}` | ADMIN |
| GET | `/api/admin/genres`, `/api/admin/genres/{id}` | ADMIN |
| GET | `/api/admin/rooms`, `/api/admin/rooms/{id}` | ADMIN |
| GET | `/api/admin/seats`, `/api/admin/seats/{id}` | ADMIN |
| GET | `/api/admin/users`, `/api/admin/users/{id}` | ADMIN |
| GET | `/api/admin/statistics` | ADMIN |
| POST | `/api/admin/movies`, `/api/admin/genres`, `/api/admin/rooms`, `/api/admin/seats`, `/api/admin/users`, `/api/admin/showtimes` | ADMIN |
| POST | `/api/admin/rooms/{id}/seats` | ADMIN |
| PUT, DELETE | `/api/admin/movies/{id}`, `/api/admin/genres/{id}`, `/api/admin/rooms/{id}`, `/api/admin/seats/{id}`, `/api/admin/users/{id}`, `/api/admin/showtimes/{id}` | ADMIN |
| GET | `/api/showtimes`, `/api/showtimes/{id}`, `/api/showtimes/{id}/seats` | ADMIN, STAFF |
| GET | `/api/bookings`, `/api/bookings/{id}` | ADMIN, STAFF |
| POST | `/api/bookings` | ADMIN, STAFF |

Quyền DELETE không bỏ qua ràng buộc lịch sử/nghiệp vụ khi triển khai sau. Quyền hủy STAFF trong business-rules vẫn **Đề xuất chờ Kha duyệt**: chưa cấp cho STAFF; ADMIN có POST `/api/bookings/{id}/cancel` theo mục 9. PATCH chưa cấp. Không có matcher mở mọi `/api/**` hoặc `/api/admin/**`.

Route ngoài danh sách: đọc ẩn danh 401, đã đăng nhập 403; request ghi thiếu/sai CSRF trả 403 CSRF_INVALID trước kiểm tra quyền. Security chạy trước MVC: không suy ra mọi URL thiếu là 404 hoặc method sai là 405.

Tài nguyên giao diện có sẵn (chỉ bảo vệ đường dẫn, không sửa frontend):

| Method | Đường dẫn | Quyền |
|---|---|---|
| GET | `/`, `/index.html`, `/login.html`, `/assets/**` | Công khai |
| GET | `/admin/showtimes.html`, `/admin/bookings.html` | ADMIN, STAFF |
| GET | `/admin/dashboard.html`, `/admin/movies.html`, `/admin/genres.html`, `/admin/rooms.html`, `/admin/seats.html`, `/admin/users.html`, `/admin/statistics.html` | ADMIN |

DispatcherType.ERROR được phép tới fallback lỗi, không mở request thường tới API. Không redirect HTML mặc định hoặc bật HTTP Basic.

## 4. Lỗi JSON thống nhất

Security filter dùng AuthenticationEntryPoint/AccessDeniedHandler ghi cùng định dạng MVC; advice không tự bắt mọi lỗi filter.

```json
{
  "timestamp":"2026-10-27T22:00:00+07:00",
  "status":403,
  "code":"CSRF_INVALID",
  "message":"CSRF token thiếu hoặc không hợp lệ.",
  "path":"/api/auth/login",
  "fieldErrors":{}
}
```

| Trường | Quy ước |
|---|---|
| timestamp | ISO-8601 offset +07:00, có thể có phần giây thập phân |
| status | Số, trùng HTTP status |
| code | Mã ổn định để giao diện xử lý |
| message | Thông báo an toàn, không dùng chuỗi làm mã xử lý |
| path | Đường dẫn không kèm query string |
| fieldErrors | Object tên trường -> mảng thông báo, luôn có; `{}` nếu không có lỗi trường |

Ví dụ `"fieldErrors":{"email":["Giá trị không hợp lệ."],"password":["Giá trị không hợp lệ."]}`. Không phản chiếu giá trị đã nhập. Không trả trace, SQL, exception class, JDBC URL hay credential.

| HTTP | code | Đường thực thi |
|---|---|---|
| 400 | INVALID_REQUEST, VALIDATION_FAILED | JSON/login sai, Bean Validation |
| 401 | UNAUTHENTICATED | Không có phiên xác thực khi gọi route bảo vệ |
| 401 | INVALID_CREDENTIALS | Xác thực thất bại, kể cả INACTIVE |
| 403 | CSRF_INVALID | Thiếu/sai token ở request ghi |
| 403 | FORBIDDEN | Đã xác thực nhưng thiếu quyền hoặc route không được cấp |
| 404 | RESOURCE_NOT_FOUND | Qua Security nhưng không có tài nguyên/controller |
| 405 | METHOD_NOT_ALLOWED | MVC nhận method không hỗ trợ sau khi qua Security |
| 406 | NOT_ACCEPTABLE | Accept không hỗ trợ |
| 409 | DATA_CONFLICT hoặc code nghiệp vụ | Handler nền tảng, mới test fixture, chưa có CRUD |
| 415 | UNSUPPORTED_MEDIA_TYPE | Content-Type không hỗ trợ sau khi qua Security |
| 500 | INTERNAL_ERROR | Lỗi không dự kiến, đã làm sạch |
| 503 | DATABASE_UNAVAILABLE | Health không truy cập DB sau startup |

Lỗi mạng không có response HTTP cần thông báo kết nối riêng. Đạt đọc status/code, hiển thị message chung và fieldErrors cạnh ô nhập; không parse message để đoán mã lỗi.

## 5. Kiểm thử và bàn giao

- 84 test đã đạt: 12 nền tảng, 5 MySQL nền tảng, 14 Security, 13 Catalog, 12 RoomSeat, 13 Showtime và 15 Booking. Catalog bao phủ HTTP CRUD thật, rollback, bảo vệ lịch sử và số truy vấn; các test xác thực cũ tiếp tục đạt. Security test dùng Tomcat/MySQL thật, CookieManager và CSRF lấy qua HTTP; không dùng mock user/token.
- Bộ regression Security dùng controller users **chỉ trong src/test** (không đóng gói JAR). Bộ Catalog kiểm tra quyền với controller phim/thể loại thật, không mock user hoặc token.
- Lượt CRUD dùng DB thử mới riêng `cinema_management_test_catalog_20261027_225624`, Laragon MySQL 8.4.3 cổng 3306; DB thử bước trước là `cinema_management_test_backend_20261027`. Fixture tài khoản ngẫu nhiên được dọn sau test, không có credential dùng chung.
- [Postman collection](postman/cinema.postman_collection.json): đặt baseUrl và credential local trong biến riêng của Postman, không ghi vào file chia sẻ. ADMIN gọi module chưa làm như users nhận 404; phim/thể loại dùng hợp đồng mục 6. STAFF cùng request có token hợp lệ nhận 403.
- Khóa/đổi vai trò tài khoản tác động lần đăng nhập mới; thu hồi ngay phiên đang mở và mô hình nhiều server chưa triển khai ở bước này.
- **Chưa kiểm chứng trên máy Đạt hoặc HTTPS.** Quyền hủy và các đề xuất D-* tiếp tục chờ Kha duyệt.

Tham khảo Spring Security 6.5: [session trong controller](https://docs.spring.io/spring-security/reference/6.5/servlet/authentication/session-management.html), [CSRF](https://docs.spring.io/spring-security/reference/6.5/servlet/exploits/csrf.html), [logout](https://docs.spring.io/spring-security/reference/6.5/servlet/authentication/logout.html).


## 6. CRUD thể loại và phim — đã triển khai

### 6.1 Method, quyền và HTTP status

Cả hai nhóm **chỉ ADMIN**, kể cả GET. Cookie/CSRF theo mục 1; STAFF có phiên/token hợp lệ vẫn 403 FORBIDDEN. Chưa xác thực GET nhận 401; request ghi thiếu/sai CSRF bị 403 CSRF_INVALID trước kiểm tra quyền.

| Method | Thể loại | Phim | Thành công |
|---|---|---|---|
| POST | `/api/admin/genres` | `/api/admin/movies` | 201 + JSON chi tiết + Location tương đối tới tài nguyên mới |
| GET | `/api/admin/genres` | `/api/admin/movies` | 200 + trang dữ liệu |
| GET | `/api/admin/genres/{id}` | `/api/admin/movies/{id}` | 200 + JSON chi tiết |
| PUT | `/api/admin/genres/{id}` | `/api/admin/movies/{id}` | 200 + JSON chi tiết |
| DELETE | `/api/admin/genres/{id}` | `/api/admin/movies/{id}` | 204, body rỗng |

ID phải nguyên dương trong miền Long Java; ID sai định dạng trả 400, ID hợp lệ không tồn tại của tài nguyên GET/PUT/DELETE trả 404 RESOURCE_NOT_FOUND. Không hỗ trợ PATCH.

### 6.2 Request thể loại

POST và PUT dùng cùng body JSON:

```json
{"name":"Khoa học viễn tưởng","description":"Mô tả thể loại"}
```

| Trường | Kiểu | Quy tắc |
|---|---|---|
| name | string | Bắt buộc, không trắng, tối đa 100 ký tự; strip khoảng trắng hai đầu khi lưu |
| description | string hoặc null | Tùy chọn, tối đa 500 ký tự; rỗng/trắng/null hoặc bỏ qua => null |

**PUT thay toàn bộ name và description**. name thiếu/null là 400; description bị bỏ qua sẽ được xóa thành null theo hợp đồng này. Muốn giữ mô tả phải gửi lại giá trị đã đọc từ GET. Không có code/status cho thể loại vì schema không có các cột đó.

Response ví dụ:

```json
{"id":7,"name":"Khoa học viễn tưởng","description":"Mô tả thể loại"}
```

Tên duy nhất theo UNIQUE + collation `utf8mb4_0900_ai_ci`, không phân biệt hoa/thường và dấu. Ví dụ `Hành động` và `Hanh dong` có thể xung đột theo MySQL; frontend không tự suy luận là hai tên khác nhau. Trùng trả 409 GENRE_NAME_EXISTS với fieldErrors.name. Xung đột UNIQUE xảy ra đồng thời vẫn bị database chặn, có thể trả 409 DATA_CONFLICT.

DELETE thể loại đã gắn bất kỳ phim nào: 409 GENRE_IN_USE. Không xóa phim hoặc tự gỡ liên kết để vượt ràng buộc.

### 6.3 Request phim

POST/PUT có cùng cấu trúc, **không gửi Entity, id, timestamps hoặc giờ kết thúc suất**:

```json
{
  "code":"MOV-SCI-001",
  "title":"Hành trình qua các vì sao",
  "description":"Phim minh họa để dựng giao diện, không phải dữ liệu rạp thật.",
  "durationMinutes":120,
  "minimumAge":13,
  "posterUrl":"https://example.invalid/poster.jpg",
  "trailerUrl":null,
  "status":"ACTIVE",
  "genreIds":[1,3]
}
```

Các genreIds trong ví dụ phải tồn tại trên DB đang chạy; lấy qua GET genres, không hard-code ID của máy Kha. `example.invalid` chỉ là placeholder, không có ảnh thật. Backend không tải URL hoặc kiểm tra tài nguyên ở đó tồn tại.

| Trường | Kiểu | Bắt buộc / quy tắc |
|---|---|---|
| code | string | Có, 1–32 ký tự ASCII thuộc A–Z/a–z/0–9/dấu chấm/gạch dưới/gạch ngang; lưu uppercase; duy nhất không phân biệt hoa/thường |
| title | string | Có, không trắng, tối đa 200 ký tự; strip hai đầu; **được trùng tên** |
| description | string/null | Tùy chọn, tối đa 65535 byte UTF-8 theo TEXT; trắng/null/bỏ qua => null |
| durationMinutes | JSON integer | Có, 1–65535 theo SMALLINT UNSIGNED; không nhận số lẻ hoặc chuỗi số |
| minimumAge | JSON integer | Có, 0–18 theo CHECK hiện có; không nhận số lẻ hoặc chuỗi số |
| posterUrl | string/null | Tùy chọn, tối đa 2048 ký tự; trắng/null/bỏ qua => null |
| trailerUrl | string/null | Tương tự posterUrl |
| status | string | Có, đúng ACTIVE hoặc INACTIVE; không có trạng thái khác |
| genreIds | array JSON integer | Có, không null/rỗng; tối đa 100 phần tử đầu vào; từng ID nguyên dương; loại trùng và trả theo ID tăng |

URL nếu có phải URI tuyệt đối HTTP/HTTPS, có host hợp lệ, port nếu có từ 1–65535 và không có user:password. Chặn javascript:, data:, file:, ftp: và đường dẫn tương đối. Không có chức năng upload hoặc backend fetch URL. Khi hiển thị mô tả/tên ở frontend phải dùng textContent hoặc cơ chế escape, không coi dữ liệu là HTML.

**PUT thay toàn bộ** code, title, description, durationMinutes, minimumAge, posterUrl, trailerUrl, status và danh sách liên kết genreIds. Các trường bắt buộc bị bỏ qua/null => 400, không tự lấy giá trị cũ. Ba trường tùy chọn description/posterUrl/trailerUrl bị bỏ qua hoặc gửi null/rỗng => xóa thành null; muốn giữ phải gửi lại dữ liệu GET. Không có cập nhật từng phần. Không có mặc định ngầm ACTIVE/tuổi 0 ở API mặc dù SQL có default.

Danh sách genreIds thay toàn bộ trong cùng giao dịch với phim. `[3,1,3]` được chuẩn hóa thành `[1,3]`. Rỗng hoặc bỏ qua: 400 VALIDATION_FAILED. Có ID dương không tồn tại: **400 UNKNOWN_GENRES**, fieldErrors.genreIds; không dùng 404 cho tham chiếu sai trong body. Toàn bộ thao tác rollback, không còn phim mới hoặc liên kết dở dang, phim cũ không bị thay đổi.

Response chi tiết (ví dụ minh họa):

```json
{
  "id":8,"code":"MOV-SCI-001","title":"Hành trình qua các vì sao",
  "description":"Phim minh họa để dựng giao diện, không phải dữ liệu rạp thật.",
  "durationMinutes":120,"minimumAge":13,
  "posterUrl":"https://example.invalid/poster.jpg","trailerUrl":null,"status":"ACTIVE",
  "genreIds":[1,3],
  "genres":[
    {"id":1,"name":"Hành động","description":"Thể loại minh họa"},
    {"id":3,"name":"Phiêu lưu","description":"Thể loại minh họa"}
  ],
  "createdAt":"2026-10-27T23:00:00.123456","updatedAt":"2026-10-27T23:00:00.123456"
}
```

Timestamps phim là LocalDateTime từ DATETIME(6), **giờ Việt Nam +07:00 theo quy ước**, chuỗi không mang offset; frontend không tự coi là UTC. Timestamp của envelope lỗi vẫn có offset như mục 4. Các genre trong response xếp theo ID tăng; response không có quan hệ vòng lặp hoặc Entity.

### 6.4 Bảo vệ lịch sử và các đề xuất chưa duyệt

Không tự coi D-03/D-04 trong business-rules là đã được duyệt. Bản triển khai hiện áp dụng các giới hạn bảo vệ sau, ghi rõ để Kha duyệt tiếp:

- Tuổi 0–18 và ACTIVE/INACTIVE **giữ đúng schema hiện có**, không tạo bộ mã tuổi pháp lý hoặc danh sách rời rạc mới. 0 là giá trị nội bộ, không khẳng định phân loại pháp lý.
- **Đề xuất chờ Kha duyệt:** phim có ít nhất một thể loại; mã ASCII như bảng trên; tối đa 100 genreIds là giới hạn request kỹ thuật.
- Xóa phim có **bất kỳ suất chiếu nào**, kể cả đã hủy/kết thúc: 409 MOVIE_HAS_SHOWTIMES. Phim chưa có suất được gỡ bảng nối rồi xóa trong cùng giao dịch; thể loại được giữ lại.
- **Đề xuất bảo vệ tạm thời chờ Kha duyệt:** khóa sửa thời lượng/tuổi khi phim đã có bất kỳ suất nào, kể cả lịch sử đã kết thúc/hủy: 409 MOVIE_SCHEDULE_FIELDS_LOCKED. Đây là phương án chặt hơn đề xuất BR-005 (chỉ khóa khi có suất chưa kết thúc), không tuyên bố BR-005 đã chốt. Có thể sửa metadata/genre khi hợp lệ; không bao giờ sửa snapshot hoặc giờ kết thúc suất cũ.
- **Đề xuất chờ Kha duyệt theo BR-004:** chưa cho INACTIVE nếu còn suất SCHEDULED có ends_at > giờ server: 409 MOVIE_HAS_UNFINISHED_SHOWTIMES. Khi chỉ còn suất đã kết thúc/hủy, có thể ngừng sử dụng và giữ lịch sử. ACTIVE trở lại không tự khôi phục suất đã hủy.

Giao dịch ghi dùng READ COMMITTED. Update/delete phim khóa dòng phim trước kiểm tra suất, rồi khóa các genre liên quan; xóa/sửa genre khóa dòng genre. FK/UNIQUE vẫn chặn race và không cascade REMOVE. **Module lịch chiếu sau phải khóa phim và kiểm tra trạng thái dưới khóa theo docs/database.md**; không tuyên bố CRUD này đã triển khai kiểm tra trùng lịch hoặc module suất chiếu.

### 6.5 Tìm kiếm, lọc, phân trang

| Query | Mặc định | Quy tắc |
|---|---|---|
| q | rỗng | Tối đa 200 ký tự; strip; tìm chứa trong tên genre hoặc code/title phim, theo collation MySQL; %, _ và ! được tìm như ký tự thường |
| page | 0 | Số trang bắt đầu từ **0**, phải >=0 |
| size | 20 | Từ 1–100 |
| sort | id | Genre: id/name. Phim: id/code/title/durationMinutes/minimumAge/status/createdAt/updatedAt |
| direction | asc | asc/desc, không phân biệt hoa thường |
| status | không lọc | Chỉ phim; ACTIVE hoặc INACTIVE, sai giá trị là 400 |
| genreId | không lọc | Chỉ phim; ID nguyên dương, lọc thành viên thể loại. ID không có phim hoặc không tồn tại => trang rỗng 200 |

Mọi sort ngoài whitelist trả 400, không nối tên cột hoặc từ khóa người dùng vào SQL. Tìm kiếm có bind parameter; genre filter dùng EXISTS để không nhân số phim. Khi sort không phải id, thêm **id ASC** làm tiêu chí phụ ổn định. Phân trang phim lấy trang/count trước, rồi một truy vấn projection thể loại cho cả trang; không fetch join collection trên truy vấn phân trang. Trang vượt phạm vi: items rỗng, total/totalPages vẫn là tổng thực.

Ví dụ `GET /api/admin/movies?q=MOV-SCI&status=ACTIVE&genreId=1&page=0&size=20&sort=title&direction=asc`:

```json
{
  "items":[{"id":8,"code":"MOV-SCI-001","title":"Hành trình qua các vì sao","description":null,
    "durationMinutes":120,"minimumAge":13,"posterUrl":null,"trailerUrl":null,"status":"ACTIVE",
    "genreIds":[1],"genres":[{"id":1,"name":"Hành động","description":"Thể loại minh họa"}],
    "createdAt":"2026-10-27T23:00:00","updatedAt":"2026-10-27T23:00:00"}],
  "total":1,"page":0,"size":20,"totalPages":1
}
```

Genre list có cùng envelope, items là GenreResponse. Tập rỗng có total=0, totalPages=0. Để dựng selector trên form, tải các trang genre tới khi đủ total; không giả định size=100 trả mọi genre.

### 6.6 Lỗi và dữ liệu cho giao diện

| HTTP | code | fieldErrors / xử lý |
|---|---|---|
| 400 | VALIDATION_FAILED | Tên/độ dài/giá trị/URL/page/size/sort sai; lỗi theo trường |
| 400 | INVALID_REQUEST | JSON hỏng, số sai định dạng, duration/age/genreIds có số lẻ; fieldErrors có thể rỗng |
| 400 | UNKNOWN_GENRES | fieldErrors.genreIds; tải lại selector, không báo đã lưu |
| 404 | RESOURCE_NOT_FOUND | ID phim/genre của route không tồn tại |
| 409 | GENRE_NAME_EXISTS | fieldErrors.name |
| 409 | MOVIE_CODE_EXISTS | fieldErrors.code |
| 409 | GENRE_IN_USE | Thể loại đang dùng, không xóa |
| 409 | MOVIE_HAS_SHOWTIMES | Phim có lịch sử suất, không xóa |
| 409 | MOVIE_SCHEDULE_FIELDS_LOCKED | Thời lượng/tuổi bị khóa; không tự sửa giờ suất |
| 409 | MOVIE_HAS_UNFINISHED_SHOWTIMES | Xử lý các suất liên quan trước khi ngừng phim |
| 409 | DATA_CONFLICT | Database chặn UNIQUE/FK khi race; không trả SQL, có thể không xác định được trường |
| 409 | CONCURRENT_MODIFICATION | Không lấy được khóa/ghi đồng thời; tải lại trước khi thử lại |

Ví dụ validation: envelope mục 4 với status=400, code=VALIDATION_FAILED, path=/api/admin/movies, fieldErrors={"posterUrl":["URL phải là HTTP/HTTPS tuyệt đối, có host và không chứa thông tin đăng nhập."]}. Ví dụ không thể xóa: status=409, code=MOVIE_HAS_SHOWTIMES, message="Không thể xóa phim đã có suất chiếu.", fieldErrors={}.

Dữ liệu minh họa sẵn trong seed: ba genre Hành động/Hoạt hình/Phiêu lưu và ba phim DEMO-M01/M02/M03, có phim gắn nhiều thể loại, bốn suất liên quan. Dùng GET để lấy ID, không chạy lại seed trên DB có dữ liệu. Phim seed đã có suất nên DELETE bị 409; dùng Postman tạo phim riêng để thử xóa thành công. Poster/trailer nullable nên Đạt cần hiển thị ảnh thay thế.

Postman có luồng tạo hai genre → tạo phim → sửa liên kết → tìm/lọc → thử xóa genre đang dùng → ngừng phim → xóa phim/genre vừa tạo. Tên/mã thử được sinh ngẫu nhiên, không xóa phim seed. **Chưa kiểm chứng tích hợp với frontend thật của Đạt.**


## 7. Phòng chiếu và sơ đồ ghế vật lý — đã triển khai

### 7.1 Endpoint và quyền

Toàn bộ các API sau **chỉ ADMIN**, gồm cả đọc sơ đồ. STAFF chỉ đọc thông tin cần thiết trong module lịch/đặt chỗ sau này theo business-rules; chưa tự mở quyền quản trị. Cookie và CSRF không đổi: GET cần phiên; POST/PUT/DELETE cần thêm X-CSRF-TOKEN. Chưa đăng nhập GET: 401; STAFF với phiên/token hợp lệ: 403 FORBIDDEN; thiếu/sai token ở thao tác ghi: 403 CSRF_INVALID.

| Method | Đường dẫn | Thành công |
|---|---|---|
| POST | `/api/admin/rooms` | 201 + RoomResponse, Location tới phòng |
| GET | `/api/admin/rooms` | 200 PageResponse |
| GET | `/api/admin/rooms/{roomId}` | 200 RoomResponse |
| PUT | `/api/admin/rooms/{roomId}` | 200 RoomResponse |
| DELETE | `/api/admin/rooms/{roomId}` | 204, không body |
| GET | `/api/admin/rooms/{roomId}/seats` | 200 SeatMapResponse |
| POST | `/api/admin/rooms/{roomId}/seats` | 201 SeatResponse, Location tới sơ đồ |
| PUT | `/api/admin/rooms/{roomId}/seats/{seatId}` | 200 SeatResponse |
| DELETE | `/api/admin/rooms/{roomId}/seats/{seatId}` | 204 |
| POST | `/api/admin/rooms/{roomId}/seats/bulk` | 201 SeatMapResponse, Location tới sơ đồ |

Không có reset, chuyển ghế giữa phòng, GET chi tiết ghế độc lập hoặc API phẳng `/api/admin/seats` chạy thật. ID phải nguyên dương trong miền Long; ID sai định dạng/không dương: 400. Room không tồn tại hoặc seat không thuộc đúng room trên URL: **404 RESOURCE_NOT_FOUND**, kể cả seatId thực sự có ở phòng khác. Không sửa/xóa ghế bằng ID mà bỏ qua roomId.

### 7.2 Request phòng và danh sách

POST/PUT dùng cùng body, cả ba trường đều bắt buộc, không tự lấy giá trị cũ khi PUT:

```json
{"code":"P03","name":"Phòng chiếu 3","status":"ACTIVE"}
```

code: 1–32 ký tự ASCII chữ/số/dấu chấm/gạch dưới/gạch ngang, lưu uppercase; name: không trắng, tối đa 100 ký tự, strip hai đầu. Status đúng ACTIVE hoặc INACTIVE theo CHECK hiện có. Mã duy nhất không phân biệt hoa/thường do UNIQUE trong MySQL; trùng trả 409 ROOM_CODE_EXISTS với fieldErrors.code (race có thể trả DATA_CONFLICT). Tên phòng được trùng.

RoomResponse: `id`, `code`, `name`, `status`, `seatCount`, `activeSeatCount`, `createdAt`. Thời gian theo LocalDateTime giờ Việt Nam như response phim.

Schema **không có cột capacity của phòng**. seatCount là số ghế vật lý hiện có, gồm INACTIVE; activeSeatCount là số ghế có trạng thái ACTIVE, độc lập với trạng thái phòng. Đây không phải số ghế trống của suất, không phải giới hạn thiết kế, không ghi hai số này từ request. Phòng INACTIVE vẫn có thể có activeSeatCount > 0 nhưng không vì vậy được bán ghế. `showtimes.capacity_snapshot` là dữ liệu khác, API này không cập nhật snapshot.

GET rooms có `q`, `status`, `page`, `size`, `sort`, `direction`: giống quy ước mục 6 (page bắt đầu 0, size 1–100/default20; q tối đa 200, tìm chứa code/name và escape %, _, !). sort chỉ id/code/name/status/createdAt, mặc định id ASC; thêm id ASC làm tiêu chí phụ. status bỏ qua thì không lọc, nếu gửi phải ACTIVE/INACTIVE. Envelope items/total/page/size/totalPages; phòng không ghế có hai count=0. Count ghế được aggregate một lần cho cả trang, không truy vấn từng phòng.

### 7.3 Thêm/sửa ghế

```json
{"rowLabel":"A","seatNumber":2,"seatType":"VIP","status":"ACTIVE"}
```

Tất cả trường bắt buộc cho POST và PUT. PUT thay toàn bộ bốn trường này, không nhận roomId để di chuyển ghế. Backend chỉ ghi roomId lấy từ URL. Code ghế do server tạo = rowLabel đã uppercase + seatNumber (không thêm số 0 đầu); không có input code độc lập.

**Đề xuất định dạng chờ Kha duyệt:** rowLabel là 1–4 chữ ASCII A–Z, chấp nhận a–z rồi uppercase, không chứa số/khoảng trắng; seatNumber là JSON integer 1–65535 theo SMALLINT UNSIGNED, không nhận số lẻ hoặc chuỗi số. Ví dụ a + 2 thành A2, AA + 10 thành AA10. UNIQUE(room_id,code) và UNIQUE(room_id,row_label,seat_number) bảo vệ trùng. Hai phòng khác nhau có A1 là hợp lệ.

seatType chỉ STANDARD/VIP. Status vật lý chỉ ACTIVE/INACTIVE. Ghế hỏng/ngừng sử dụng dùng **INACTIVE**; schema chưa phân biệt lý do hỏng với ngừng dùng, không tự tạo BROKEN hoặc cột lý do. Không dùng BOOKED/AVAILABLE trên ghế vật lý. Trạng thái trống/đã đặt thuộc từng suất, chưa triển khai ở bước này.

SeatResponse gồm `id`, `roomId`, `code`, `rowLabel`, `seatNumber`, `seatType`, `status`. Không trả Entity hoặc quan hệ vòng lặp.

### 7.4 Tạo sơ đồ hàng loạt

```json
{"rows":["A","B","C"],"seatsPerRow":10,"seatType":"STANDARD"}
```

**Đề xuất MVP chờ Kha duyệt:** 1–26 hàng trong một request; hàng theo định dạng trên và không lặp sau uppercase (A/a là trùng); seatsPerRow là JSON integer 1–50; tổng ghế tạo tối đa 500. Phòng cũng bị giới hạn kỹ thuật tối đa 500 ghế cho thao tác thêm lẻ. seatType bắt buộc STANDARD/VIP, trạng thái ghế tạo hàng loạt luôn ACTIVE. Ghế mỗi hàng đánh số liên tục từ 1 đến seatsPerRow. Có thể chỉnh VIP/INACTIVE sau nếu nghiệp vụ còn cho phép.

Chỉ tạo khi **phòng chưa có ghế và chưa từng có suất**. Không thêm vào sơ đồ đang có, không ghi đè hoặc xóa sơ đồ cũ. Request được kiểm tra toàn bộ trước ghi. Trong giao dịch READ COMMITTED, khóa dòng phòng trước kiểm tra rỗng/giới hạn rồi ghi tất cả ghế. Có lỗi => rollback toàn bộ. Hai request cùng phòng được tuần tự hóa bởi khóa này; UNIQUE của DB vẫn bảo vệ cuối cùng. Request đầu thành công 201, gửi lại hoặc request cạnh tranh tiếp theo 409 SEAT_LAYOUT_NOT_EMPTY, không tạo bản ghi trùng. Không tự retry thao tác tạo sau timeout; đọc sơ đồ để biết kết quả.

Phòng INACTIVE chưa có lịch vẫn được chuẩn bị sơ đồ; trạng thái ghế vật lý tách khỏi việc phòng có đang mở sử dụng hay không.

### 7.5 Quy tắc bảo vệ lịch sử và phối hợp module sau

D-05/BR-007/008 chưa được duyệt đầy đủ; áp dụng hàng rào bảo vệ tạm thời, **Đề xuất chờ Kha duyệt**, không tự coi là chính sách đã chốt:

- Chỉ xóa phòng chưa từng có suất **và không còn ghế**. Còn ghế thì ADMIN xóa từng ghế hợp lệ trước; không cascade hoặc tự xóa ghế hộ người dùng.
- Sau bất kỳ suất nào của phòng, kể cả đã hủy/kết thúc: khóa thêm/xóa ghế và đổi hàng/số/mã/loại. Sửa không đổi cấu trúc vẫn có thể thực hiện nếu trạng thái hợp lệ.
- **Phương án chặt hơn BR-008 chờ duyệt:** nếu phòng còn suất SCHEDULED có ends_at > giờ server, chặn mọi thay đổi trạng thái phòng/ghế, kể cả ghế chưa có đặt chỗ. Tránh làm sai sức chứa hoặc điều kiện áp dụng khi chính sách suất tương lai chưa thống nhất.
- Nếu chỉ còn suất kết thúc/hủy, cho đổi ACTIVE/INACTIVE vật lý; giữ nguyên phòng/ghế/loại, snapshot, giá và lịch sử booking. Không tự hủy suất hoặc booking để đổi trạng thái. Giá của đơn cũ không được cập nhật theo loại/trạng thái hiện tại.
- Code/name phòng có thể sửa dưới khóa; booking đã lưu room_code_snapshot không đổi. Không tự thay capacity_snapshot của suất.

Mọi thao tác ghi phòng/ghế khóa **dòng phòng tồn tại trước**, gồm phòng chưa có ghế, dùng READ COMMITTED. Khi lịch/đặt chỗ được triển khai, phải cùng tuân thứ tự `phim -> phòng -> suất -> đơn -> ghế` theo docs/database.md, ID tăng trong mỗi nhóm; sau khóa kiểm tra lại trạng thái/ngữ cảnh. Module này chỉ cần khóa phòng, không quay lại khóa phim ở phía sau. FK/UNIQUE không thay thế kiểm tra lịch, và không có module lịch/đặt chỗ mới ở bước này.

### 7.6 Hai ví dụ sơ đồ cho Đạt

Hai object dưới đây tương ứng hai lần GET sơ đồ, ID/thời gian chỉ minh họa:

```json
[
  {
    "room":{"id":101,"code":"P01","name":"Phòng 1","status":"ACTIVE","seatCount":2,"activeSeatCount":1,"createdAt":"2026-10-27T23:00:00"},
    "seats":[
      {"id":1001,"roomId":101,"code":"A2","rowLabel":"A","seatNumber":2,"seatType":"STANDARD","status":"ACTIVE"},
      {"id":1002,"roomId":101,"code":"A10","rowLabel":"A","seatNumber":10,"seatType":"VIP","status":"INACTIVE"}
    ]
  },
  {
    "room":{"id":102,"code":"P02","name":"Phòng 2","status":"ACTIVE","seatCount":2,"activeSeatCount":2,"createdAt":"2026-10-27T23:00:00"},
    "seats":[
      {"id":2001,"roomId":102,"code":"A1","rowLabel":"A","seatNumber":1,"seatType":"STANDARD","status":"ACTIVE"},
      {"id":2002,"roomId":102,"code":"B1","rowLabel":"B","seatNumber":1,"seatType":"VIP","status":"ACTIVE"}
    ]
  }
]
```

Thực tế API trả **một object** room/seats cho roomId được gọi. Phòng chưa có ghế trả seats=[], count=0 (200). Sắp xếp hàng kiểu cột chữ: A…Z, AA…AZ, BA…; trong hàng theo seatNumber số nguyên rồi id; A2 đứng trước A10. Schema không có tọa độ pixel/x/y/lối đi riêng; frontend nhóm rowLabel và vẽ các seatNumber hiện có, không tự suy ra ghế thiếu là đã đặt. Hiển thị INACTIVE khác ACTIVE; chưa có trạng thái theo suất.

### 7.7 Mã lỗi bổ sung

| HTTP | code | Ý nghĩa |
|---|---|---|
| 400 | VALIDATION_FAILED | Trường nhập, hàng/số/loại/trạng thái/giới hạn bulk sai; fieldErrors theo trường |
| 400 | INVALID_REQUEST | JSON/số nguyên sai định dạng |
| 404 | RESOURCE_NOT_FOUND | Phòng không tồn tại hoặc ghế không thuộc đúng phòng |
| 409 | ROOM_CODE_EXISTS | fieldErrors.code |
| 409 | SEAT_POSITION_EXISTS | fieldErrors.seatNumber; mã/vị trí trùng trong phòng |
| 409 | ROOM_HAS_SEATS | Xóa phòng còn ghế bị chặn |
| 409 | ROOM_HAS_SHOWTIMES | Xóa phòng có bất kỳ suất bị chặn |
| 409 | SEAT_LAYOUT_LOCKED | Có lịch sử suất nên khóa cấu trúc ghế |
| 409 | SEAT_LAYOUT_NOT_EMPTY | Bulk chỉ áp dụng phòng chưa có ghế |
| 409 | ROOM_HAS_UNFINISHED_SHOWTIMES | Chặn đổi trạng thái phòng còn suất chưa kết thúc |
| 409 | SEAT_STATUS_LOCKED | Chặn đổi trạng thái ghế khi phòng còn suất chưa kết thúc |
| 409 | DATA_CONFLICT, CONCURRENT_MODIFICATION | DB/khóa chặn xung đột; response được làm sạch |

Ví dụ xóa phòng: envelope lỗi chung với status=409, code=ROOM_HAS_SEATS, message="Phòng còn ghế; hãy xóa từng ghế hợp lệ trước.", fieldErrors={}. Ví dụ đổi loại ghế có lịch sử: status=409, code=SEAT_LAYOUT_LOCKED, fieldErrors={}, không cập nhật giá cũ hoặc hủy booking.

**Chưa kiểm chứng tích hợp frontend thật của Đạt.** Postman tạo riêng hai phòng và hai sơ đồ, không reset hoặc sửa seed. Các ca lịch sử/đặt chỗ chỉ là fixture SQL trong DB test, không phải API nghiệp vụ mới.

Bằng chứng phòng/ghế: DB riêng `cinema_management_test_rooms_20261027_231404`, MySQL8.4.3/3306, 12 RoomSeat test đạt cùng 44 regression cũ. Test cạnh tranh tạo layout chỉ một201, một409; test lỗi sau INSERT/flush đầu tiên chứng minh rollback cả bulk. Hai fixture booking/lịch sử và user INACTIVE không có hash được giữ trong DB thử theo trigger bảo vệ; tài khoản đăng nhập và fixture không lịch sử đã dọn. Xem README mục12.


## 8. Quản lý lịch chiếu — đã triển khai

### 8.1 URL, quyền và HTTP

| Method | URL | Quyền | Thành công |
|---|---|---|---|
| POST | `/api/admin/showtimes` | ADMIN + CSRF | 201, Location tới chi tiết quản trị |
| GET | `/api/admin/showtimes` | ADMIN | 200 PageResponse |
| GET | `/api/admin/showtimes/{id}` | ADMIN | 200 EntityModel/HATEOAS |
| PUT | `/api/admin/showtimes/{id}` | ADMIN + CSRF | 200 EntityModel/HATEOAS |
| POST | `/api/admin/showtimes/{id}/cancel` | ADMIN + CSRF | 200 EntityModel/HATEOAS |
| DELETE | `/api/admin/showtimes/{id}` | ADMIN + CSRF | 204, body rỗng |
| GET | `/api/showtimes` | ADMIN, STAFF | 200 PageResponse |
| GET | `/api/showtimes/{id}` | ADMIN, STAFF | 200 EntityModel/HATEOAS |

Không có POST/PUT/DELETE ở route đọc `/api/showtimes`. API ghế theo suất và đặt chỗ đã có ở mục 9; các link HATEOAS lịch chiếu hiện không thay đổi. Quyền đọc chỉ thêm lịch, không mở quyền STAFF tới phim/phòng quản trị. Cookie, CSRF, 401/403 và lỗi chung giữ nguyên. API trả JSON; client tiếp tục dùng Accept: application/json. Khi dùng HAL-aware client có thể nhận application/hal+json cho detail, cùng cấu trúc trường + `_links`.

### 8.2 Body tạo/sửa toàn bộ

```json
{
  "movieId":1,
  "roomId":1,
  "startsAt":"2026-11-16T18:00:00+07:00",
  "standardPrice":80000,
  "vipPrice":100000
}
```

Cả năm trường bắt buộc cho POST/PUT. movieId/roomId phải JSON integer dương, tham chiếu tồn tại; tham chiếu hợp lệ nhưng không tồn tại trả 404. startsAt phải ISO-8601 có **đúng offset +07:00**, giây và phần lẻ giây bằng 0. Không nhận thời gian thiếu offset hoặc UTC/Z/offset khác để tránh hiểu nhầm múi giờ. Giữ offset gốc tại DTO, không tự normalize UTC trước validation. Dữ liệu lưu DATETIME theo giờ Việt Nam; JDBC và Clock hiện có không đổi.

Backend đọc phim dưới khóa, chụp `duration_minutes_snapshot`, `minimum_age_snapshot`, tính ends_at = starts_at + thời lượng đó. Không tin client gửi giờ kết thúc: `endsAt`/`status` nếu được gửi khác null trả 400 VALIDATION_FAILED; client không điều khiển trạng thái qua PUT. Phim/phòng phải ACTIVE, phòng có ít nhất một ghế vật lý ACTIVE. Sức chứa snapshot vẫn để null theo schema hiện tại; quyết định chốt sức chứa cần hoàn tất trước module đặt chỗ.

Giá là BigDecimal/VND nguyên, tối đa 12 chữ số nguyên, không âm; vipPrice >= standardPrice **theo CHECK đã tồn tại trong schema**. Không suy ra giá VIP bằng hệ số/phụ phí. **Đề xuất chờ Kha duyệt D-10:** giữ hai mức giá nhập tường minh này, chưa tự coi chính sách VIP là đã duyệt. priceVersion tạo mới=0, tăng 1 khi thay một trong hai giá; nếu có bất kỳ lịch sử booking thì chưa cho sửa, nên giá của booking cũ không thay đổi.

**Đề xuất chờ Kha duyệt D-06/D-07:** chỉ tạo/sửa với startsAt ở tương lai theo Clock server, không nhập lịch quá khứ hoặc đúng thời điểm hiện tại. Thời gian/năm phải nằm trong miền DATETIME 1000–9999, gồm giờ kết thúc và 15 phút nghỉ. PUT thay đủ movieId, roomId, startsAt và hai giá, thiếu trường là 400. Không có PATCH hoặc chế độ chỉ đổi giá riêng.

### 8.3 Khoảng nghỉ và trạng thái chiếm lịch

Chỉ suất cùng roomId có status=SCHEDULED chiếm lịch. CANCELLED không chiếm lịch. Không lọc xung đột theo ngày vì suất có thể qua nửa đêm hoặc bao trùm nhiều ngày.

Suất mới xung đột nếu đồng thời:

```text
newStart < existingEnd + 15 phút
AND existingStart < newEnd + 15 phút
```

Truy vấn tương đương: `existing.startsAt < newEnd + 15` và `existing.endsAt > newStart - 15`. Dùng dấu so sánh nghiêm ngặt: phim kết thúc 20:00 thì suất 20:15 được phép, 20:14 bị chặn. Kiểm tra cả suất trước/sau, nằm trong, bao trùm. Khi sửa, loại id của chính suất đang sửa. Cùng giờ ở phòng khác hợp lệ.

Giờ kết thúc chỉ đọc từ giá trị đã lưu, không tính lại theo Movie hiện tại khi GET. Module phim hiện khóa thời lượng/tuổi khi đã có bất kỳ suất nào; lịch không tự cập nhật hàng loạt khi phim đổi. Nếu đổi phim của một suất tương lai chưa có booking, backend chụp thời lượng/tuổi của phim đích và kiểm tra xung đột lại. Không có chức năng áp dụng thời lượng mới hàng loạt.

### 8.4 Sửa, hủy, xóa và phần chưa duyệt

Stored status chỉ SCHEDULED/CANCELLED. Response có `phase` suy ra: UPCOMING, IN_PROGRESS, COMPLETED hoặc CANCELLED. COMPLETED/IN_PROGRESS không ghi vào DB, không phải giá trị cho bộ lọc status.

**Hàng rào bảo vệ tạm thời, Đề xuất chờ Kha duyệt D-07/D-10:**

- Chỉ sửa/hủy/xóa SCHEDULED trước startsAt và **chưa từng có booking**, kể cả booking đã CANCELLED cũng khóa thao tác.
- Đã có booking: 409 SHOWTIME_HAS_BOOKINGS cho mọi thay đổi, kể cả giá. Chưa cấp quy trình hủy suất kéo theo booking vì chính sách/quyền hủy chưa được duyệt; không tự hủy đơn hoặc chuyển ghế/phòng.
- Hủy suất chưa có booking: POST cancel với `{"reason":"Lý do hủy"}`, bắt buộc không trắng, tối đa 500 ký tự. Lưu trạng thái CANCELLED, người/thời điểm/lý do, giữ bản ghi; không còn chiếm lịch.
- CANCELLED kết thúc vĩnh viễn, không khôi phục, không sửa/xóa; hủy lặp hiện trả 409 SHOWTIME_CANCELLED. Chính sách cho xóa suất đã hủy chưa có booking cần Kha duyệt riêng, chưa mở.
- Suất đã bắt đầu/kết thúc chỉ đọc: 409 SHOWTIME_STARTED cho thao tác thay đổi. Xóa hợp lệ chưa có lịch sử trả 204; không dùng DELETE thay cho hủy một suất có lịch sử.

### 8.5 Transaction và phối hợp khóa

Tất cả create/update/cancel/delete ở service có transaction **READ COMMITTED**. Không dùng synchronized hoặc chỉ SELECT trước rồi INSERT ngoài transaction.

Create: khóa phim → khóa dòng phòng tồn tại bằng PESSIMISTIC_WRITE (`FOR UPDATE`) → kiểm tra ACTIVE/ghế → chụp thời lượng/tính ends → kiểm tra xung đột → INSERT → commit. Khóa phòng được giữ cả khi phòng đang không có suất, tới cuối transaction.

Update/chuyển phòng: đọc projection ID nguồn (không tải entity vào persistence context) → khóa các phim nguồn/đích theo ID tăng → khóa các phòng nguồn/đích theo ID tăng → khóa suất → kiểm tra lại ngữ cảnh. Nếu movieId/roomId đã khác lần đọc đầu, rollback trả 409 CONCURRENT_MODIFICATION; không khóa ngược thứ tự để đuổi theo thay đổi. Sau đó kiểm tra booking/trạng thái/thời gian và xung đột ở phòng đích, loại chính nó, rồi lưu. Cancel/delete dùng cùng thứ tự nguồn.

READ COMMITTED giúp SELECT sau khi chờ khóa thấy dữ liệu đã commit của request trước; không dùng snapshot REPEATABLE READ cũ cho quyết định. Thứ tự tương thích các module hiện có: phim → phòng → suất → đơn → ghế. MovieService/RoomService/SeatService đã khóa phim/phòng tương ứng nên không cần sửa các module đó. Module đặt chỗ sau phải khóa cùng nguồn và kiểm tra lại trạng thái dưới khóa trước INSERT; FK không tự chặn booking trên một suất CANCELLED nếu người dùng có quyền SQL trực tiếp.

UNIQUE/CHECK hiện có không bảo đảm loại mọi giao khoảng. Lịch được bảo vệ bởi giao thức transaction/khóa mà mọi đường ghi phải tuân thủ. SQL trực tiếp ngoài service không có đảm bảo chống trùng lịch này. Đối với hai phòng, khóa ID tăng giảm nguy cơ deadlock; lỗi khóa vẫn được rollback/409 như định dạng chung.

### 8.6 Danh sách và lọc theo ngày

Hai route list có cùng query và envelope `items,total,page,size,totalPages` như trước; item không có `_links`, detail mới có HATEOAS.

| Query | Quy tắc |
|---|---|
| date | ISO yyyy-MM-dd; chọn suất có **ngày bắt đầu** là date theo +07:00 |
| from, to | Cung cấp đủ hai ISO OffsetDateTime +07:00; lọc startsAt trong [from,to), from<to, tối đa 31 ngày |
| roomId, movieId | ID dương; không có kết quả hoặc ID lọc không tồn tại => trang rỗng 200 |
| status | SCHEDULED hoặc CANCELLED; bỏ qua để lấy cả hai |
| page, size | Từ 0; size mặc định20, 1–100 |
| sort, direction | sort: id/startsAt/endsAt/standardPrice/vipPrice/status; mặc định startsAt ASC; direction asc/desc; id ASC phụ để ổn định |

Không kết hợp date với from/to. date chỉ hỗ trợ năm1000–9998 để chặn tràn ngày kế tiếp; from/to theo miền DATETIME. Không có tham số tìm kiếm text q ở phiên bản này; tìm lịch bằng ngày/phòng/phim/trạng thái.

Suất 23:00 ngày16 kết thúc01:00 ngày17 nằm trong date=ngày16, **không** nằm trong date=ngày17. Đây là lựa chọn theo ngày bắt đầu đã đề xuất ở business-rules, không phải lịch giao khoảng; Đạt cần hiển thị ngày kết thúc nếu khác ngày bắt đầu. Nếu sau này cần kéo cả suất bắt đầu hôm trước vào lịch ngày sau, cần hợp đồng giao khoảng riêng.

URL có dấu `+` trong query phải encode `%2B` (khác body JSON). Ví dụ `from=2026-11-16T00%3A00%3A00%2B07%3A00&to=2026-11-17T00%3A00%3A00%2B07%3A00`. Các query sai/ngày sai/method sai được làm sạch theo handler chung.

Phân trang trước rồi lấy projection phim/phòng/thông tin booking trong một query cho cả trang; không serialize association LAZY, không truy vấn từng suất theo N+1. Count và page không fetch join collection.

### 8.7 JSON mẫu và HATEOAS

Ví dụ chi tiết ADMIN, ID/ngày là minh họa:

```json
{
  "id":401,"movieId":1,"movieTitle":"Hành trình ánh sáng","posterUrl":null,
  "roomId":3,"roomCode":"P03","roomName":"Phòng 3",
  "startsAt":"2026-11-16T23:00:00+07:00","endsAt":"2026-11-17T01:00:00+07:00",
  "durationMinutes":120,"minimumAge":13,"standardPrice":80000,"vipPrice":100000,
  "priceVersion":0,"status":"SCHEDULED","phase":"UPCOMING",
  "_links":{
    "self":{"href":"/api/admin/showtimes/401"},
    "collection":{"href":"/api/admin/showtimes"},
    "movie":{"href":"/api/admin/movies/1"},
    "room":{"href":"/api/admin/rooms/3"},
    "update":{"href":"/api/admin/showtimes/401","type":"application/json"},
    "delete":{"href":"/api/admin/showtimes/401"},
    "cancel":{"href":"/api/admin/showtimes/401/cancel","type":"application/json"}
  }
}
```

Spring HATEOAS EntityModel giữ nguyên trường DTO ở root, thêm `_links`; không thêm envelope content. URL tương đối cùng origin. self/collection dùng GET; movie/room chỉ xuất hiện cho ADMIN và là GET tới API thật. update dùng PUT với đầy đủ body và CSRF, delete dùng DELETE+CSRF, cancel dùng POST reason+CSRF; link.type là media type, không phải HTTP method. Không dùng GET để gọi thao tác thay đổi.

STAFF đọc `/api/showtimes/{id}` chỉ có self/collection ở route `/api/showtimes`, gọi được với quyền STAFF. Không có link tới phim/phòng quản trị hoặc thao tác ghi. ADMIN cũng không nhận update/delete/cancel nếu đã bắt đầu, CANCELLED hoặc có bất kỳ booking. Link chỉ là hướng dẫn tại thời điểm đọc; service vẫn kiểm tra lại điều kiện khi ghi, không dựa vào việc link đã được trả trước đó.

Ví dụ danh sách dựng lịch ngày/phòng (item cùng trường như detail nhưng không `_links`):

```json
{"items":[{"id":401,"movieId":1,"movieTitle":"Hành trình ánh sáng","posterUrl":null,
  "roomId":3,"roomCode":"P03","roomName":"Phòng 3",
  "startsAt":"2026-11-16T23:00:00+07:00","endsAt":"2026-11-17T01:00:00+07:00",
  "durationMinutes":120,"minimumAge":13,"standardPrice":80000,"vipPrice":100000,
  "priceVersion":0,"status":"SCHEDULED","phase":"UPCOMING"}],
 "total":1,"page":0,"size":20,"totalPages":1}
```

Tên/poster phim, tên/mã phòng trong response là thông tin tài nguyên hiện tại; duration/minimumAge/ends là dữ liệu đã chụp/lưu của suất. Booking sau này dùng các snapshot riêng của booking để giữ lịch sử giao dịch.

### 8.8 Mã lỗi bổ sung

| HTTP | code | Xử lý |
|---|---|---|
| 400 | VALIDATION_FAILED | Trường/thời gian/offset/giá/filter sai; fieldErrors theo trường |
| 400 | INVALID_REQUEST | JSON, thời gian thiếu offset, ID/số sai định dạng |
| 404 | RESOURCE_NOT_FOUND | Suất hoặc phim/phòng tham chiếu không tồn tại |
| 409 | MOVIE_INACTIVE, ROOM_INACTIVE | Chọn tài nguyên đang sử dụng |
| 409 | ROOM_NO_ACTIVE_SEATS | Phòng cần ít nhất một ghế ACTIVE |
| 409 | SHOWTIME_CONFLICT | Trùng lịch hoặc chưa đủ15 phút nghỉ |
| 409 | SHOWTIME_HAS_BOOKINGS | Có lịch sử booking, chưa được đổi/hủy/xóa |
| 409 | SHOWTIME_STARTED | Suất đã bắt đầu, chỉ xem |
| 409 | SHOWTIME_CANCELLED | Suất đã hủy, không sửa/xóa/khôi phục |
| 409 | CONCURRENT_MODIFICATION | Ngữ cảnh đổi hoặc không lấy được khóa; tải lại, không tự ghi đè |

Lỗi có đủ timestamp/status/code/message/path/fieldErrors; không có stack trace, SQL hoặc credential. Chưa bổ sung danh sách suất xung đột trong lỗi, frontend có thể tải lại lịch phòng để hướng dẫn.

### 8.9 Bằng chứng kiểm thử và bàn giao

69/69 test đạt trên MySQL8.4.3/3306, DB mới riêng `cinema_management_test_showtimes_20261027_232747`; Hibernate validate và schema/seed gốc không đổi. 13 Showtime test gồm HTTP/CSRF thật, HATEOAS link gọi được, UTC bị từ chối theo hợp đồng, from/to hợp lệ và chặn quá31ngày, qua nửa đêm, trùng/biên15phút, lịch sử giá/booking và hồi quy các module cũ.

Ba ca đồng thời **create/edit/transfer** dùng hai HTTP client/session và hai physical MySQL CONNECTION_ID giữ đồng thời bằng test-only barrier lúc lấy connection: create nhận201/409, edit và transfer nhận200/409; mỗi ca có finalOverlapPairs=0. Evidence tại [showtime-concurrency.json](../target/showtime-concurrency.json) (artifact test, không phải mã nguồn); ID kết nối của từng lượt nằm trong JSON evidence, không hard-code qua các lượt chạy. Không dùng hai request tuần tự hoặc synchronized trong production để thay thế DB lock.

Postman hiện105request, thư mục07 giữ fixture CANCELLED cùng phim/phòng/ghế demo có chủ đích; không reset seed. JSON/body đã kiểm tra, chưa chạy collection trong ứng dụng Postman. **Chưa kiểm chứng frontend thật hoặc máy Đạt.** Các user không đăng nhập/booking lịch sử fixture được giữ trong DB thử theo trigger; credential local vẫn do mỗi người tự tạo, không có mật khẩu dùng chung.


## 9. Đặt chỗ tại quầy và hủy — đã triển khai

### 9.1 Migration bắt buộc và phạm vi

Backend mới cần **V001** sau baseline schema/seed, trước khởi động Hibernate validate. Xem README/mục nâng cấp trong database.md. Không sửa baseline hoặc tự chạy SQL lúc startup. V001 bổ sung bookings.idempotency_key/request_hash nullable cho lịch sử cũ, UNIQUE(created_by,idempotency_key), CHECK cặp metadata và trigger bất biến metadata; không thêm bảng, không xóa/backfill lịch sử. Giữ nguyên UNIQUE(showtime_id,active_seat_id) và trigger giải phóng ghế. **Mới áp dụng migration trên DB thử, chưa chạy vào DB sử dụng thực tế của Kha.**

Chỉ đặt tại quầy, CONFIRMED ngay; không giữ ghế tạm, thanh toán/hoàn tiền, khách đặt online, email/SMS. Đọc ghế không giữ chỗ.

| Method | URL | Quyền | Thành công |
|---|---|---|---|
| GET | `/api/showtimes/{id}/seats` | ADMIN, STAFF | 200 sơ đồ theo suất |
| POST | `/api/bookings` | ADMIN, STAFF + CSRF | 201 mới, 200 replay |
| GET | `/api/bookings` | ADMIN, STAFF | 200 PageResponse |
| GET | `/api/bookings/{id}` | ADMIN, STAFF | 200 BookingResponse |
| GET | `/api/bookings/by-code/{code}` | ADMIN, STAFF | 200 BookingResponse |
| POST | `/api/bookings/{id}/cancel` | ADMIN + CSRF | 200 BookingResponse |

Tra cứu trong phạm vi rạp, không nhận createdBy của client làm quyền. STAFF được tạo/tra cứu theo route đã cấp; quyền hủy STAFF vẫn **Đề xuất chờ Kha duyệt D-02/D-09**, chưa cấp kể cả đơn mình tạo. ADMIN hủy toàn bộ đơn hợp lệ trước giờ chiếu. Không có PUT/DELETE booking, không thay ghế/suất của đơn đã tạo. Nếu cần đổi lựa chọn: hủy hợp lệ rồi tạo yêu cầu mới.

### 9.2 Sơ đồ theo suất

Ví dụ JSON minh họa (đã giản lược số ghế):

```json
{
  "showtime":{"id":1,"movieId":1,"movieTitle":"Phim minh họa","posterUrl":null,
    "roomId":1,"roomCode":"P01","roomName":"Phòng 1",
    "startsAt":"2026-11-16T18:00:00+07:00","endsAt":"2026-11-16T20:00:00+07:00",
    "durationMinutes":120,"minimumAge":13,"standardPrice":80000,"vipPrice":100000,
    "priceVersion":0,"status":"SCHEDULED","phase":"UPCOMING"},
  "bookable":true,
  "seats":[
    {"id":1,"rowLabel":"A","seatNumber":1,"code":"A1","seatType":"STANDARD","physicalStatus":"ACTIVE","availability":"AVAILABLE","price":80000},
    {"id":2,"rowLabel":"A","seatNumber":2,"code":"A2","seatType":"VIP","physicalStatus":"ACTIVE","availability":"BOOKED","price":100000},
    {"id":3,"rowLabel":"A","seatNumber":3,"code":"A3","seatType":"STANDARD","physicalStatus":"INACTIVE","availability":"UNAVAILABLE","price":80000}
  ]
}
```

physicalStatus là trạng thái ghế vật lý; availability chỉ áp dụng cho **suất đang đọc**. Chỉ BookingSeat có booking_status=CONFIRMED chiếm ghế; CANCELLED không chiếm. BOOKED không chứa bookingId hoặc thông tin người đặt. Nếu ghế đã có đơn còn hiệu lực nhưng trạng thái vật lý không hợp lệ do dữ liệu bên ngoài, vẫn trả BOOKED cùng physicalStatus thực để không che lịch sử. Ghế chưa bị chiếm nhưng INACTIVE, hoặc suất/phim/phòng không cho đặt, trả UNAVAILABLE. bookable=false khi suất không SCHEDULED, đã bắt đầu, phim/phòng INACTIVE hoặc không có ghế ACTIVE.

Giá từng ghế do loại ghế hiện tại và standardPrice/vipPrice của suất quyết định. Mã/hàng/số sắp giống sơ đồ vật lý, A2 trước A10. Sơ đồ đọc dưới một snapshot REPEATABLE READ để các phần không trộn nhiều thời điểm; đây chỉ là ảnh đọc, không bảo đảm ghế vẫn trống khi POST. Không có dữ liệu cá nhân khách/nhân viên trong map. Không lưu trạng thái BOOKED lên bảng seats.

### 9.3 Tạo booking, giá và validation

Header bắt buộc:

```text
Content-Type: application/json
X-CSRF-TOKEN: <token hiện tại cùng cookie phiên>
Idempotency-Key: <UUID từ crypto.randomUUID() của frontend>
```

```json
{"showtimeId":1,"seatIds":[1,2],"priceVersion":0,"ageChecked":true}
```

| Trường | Quy tắc |
|---|---|
| showtimeId | JSON integer dương, bắt buộc; suất không tồn tại =>404 |
| seatIds | Array bắt buộc 1–20 ID nguyên dương; không trùng, không null; không tồn tại/sai phòng =>400 theo trường; ghế INACTIVE hoặc bị chiếm =>409 |
| priceVersion | JSON integer >=0, bắt buộc; phải khớp version trong sơ đồ, khác=>409 PRICE_CHANGED |
| ageChecked | boolean bắt buộc; với minimumAge snapshot >0 phải true, tuổi0 có thể false |

Giới hạn20 ghế và xác nhận kiểm tra tuổi là **Đề xuất chờ Kha duyệt** theo MVP/D-03/D-08; không tự kiểm chứng giấy tờ hoặc coi tuổi nội bộ là bộ phân loại pháp lý. Suất phải SCHEDULED, server time < startsAt, phim/phòng ACTIVE và mọi ghế ACTIVE đúng phòng. Không cho đặt từ đúng giờ bắt đầu.

Backend lấy người tạo từ CinemaPrincipal trong phiên. Các trường ngoài hợp đồng như createdBy/userId/totalAmount/unitPrice không dùng để tính hoặc ghi. Client không gửi giá/tổng để quyết định số tiền. Backend đọc giá DB dưới khóa, STANDARD dùng standardPrice, VIP dùng vipPrice; **Đề xuất chờ Kha duyệt D-10** tiếp tục dùng hai giá tường minh theo schema, không hệ số/phụ thu/thuế/giảm giá. BigDecimal cộng đơn giá VND nguyên, không phát sinh phép làm tròn. Toàn bộ đơn dùng cùng priceVersion.

Mã BK- +32 ký tự UUID hex do server sinh, UNIQUE trong DB. Lưu người/tên nhân viên, tên phim/mã phòng, mã/loại từng ghế, version giá, từng đơn giá và tổng trước INSERT. Một ghế sai hoặc bất kỳ lỗi ghi nào rollback cả phiếu và chi tiết; không có đơn rỗng/thiếu ghế/tổng lệch. Snapshot booking không đọc lại giá/loại ghế hiện tại để tính lịch sử.

Response 201 có Location `/api/bookings/{id}`, `Idempotency-Replayed: false`; replay 200 cùng Location và header true:

```json
{
  "id":21,"code":"BK-0123456789ABCDEF0123456789ABCDEF","showtimeId":1,"roomId":1,
  "movieTitle":"Phim minh họa","roomCode":"P01",
  "startsAt":"2026-11-16T18:00:00+07:00","endsAt":"2026-11-16T20:00:00+07:00",
  "createdBy":7,"createdByName":"Nhân viên quầy","createdAt":"2026-10-28T10:00:00+07:00",
  "priceVersion":0,"ageChecked":true,"totalAmount":180000,"status":"CONFIRMED",
  "cancelledAt":null,"cancelledBy":null,"cancelReason":null,
  "seats":[
    {"seatId":1,"code":"A1","seatType":"STANDARD","unitPrice":80000,"bookingStatus":"CONFIRMED"},
    {"seatId":2,"code":"A2","seatType":"VIP","unitPrice":100000,"bookingStatus":"CONFIRMED"}
  ]
}
```

Thời gian có offset +07:00. Tên phim/phòng/người/ghế và giá từ snapshot booking. Thời gian suất từ record showtime được module lịch khóa sửa khi có lịch sử booking. Không trả key/hash idempotency, entity, mật khẩu hoặc credential.

### 9.4 Idempotency và timeout

Frontend sinh UUID **một lần cho một ý định đặt**, giữ key và body khi bấm gửi lần đầu. Khi mất response/timeout, gửi lại cùng key/body để nhận đúng booking, không tự sinh key mới. Đổi suất, danh sách ghế, priceVersion hoặc ageChecked để xác nhận yêu cầu khác thì sinh key mới. Đảo thứ tự seatIds không đổi nội dung; ID trùng vẫn400, không âm thầm loại.

Key chuẩn hóa UUID lowercase, phạm vi **tài khoản đăng nhập** qua UNIQUE(created_by,key). Cùng key ở hai tài khoản là hai ý định độc lập và vẫn tranh ghế bình thường. Hash SHA-256 của chuỗi canonical showtimeId | danh sách seatIds tăng | priceVersion | ageChecked (định dạng server) lưu VARCHAR64; client không gửi hash. Giá/userId client không được công nhận nên không nằm trong hash.

Cùng actor/key/nội dung =>200 replay, không tạo phiếu mới; cùng actor/key khác nội dung=>409 IDEMPOTENCY_KEY_REUSED. Replay trả **trạng thái hiện tại của booking gốc**, kể cả CANCELLED hoặc suất đã qua giờ, không đặt lại ghế của đơn cũ. Muốn đặt lại sau hủy phải tạo key mới. Không có hạn tự hết key trong MVP; history giữ key vĩnh viễn. Record legacy trước V001 có cả key/hash null, vẫn tra cứu/hủy được nhưng không có tính năng replay cho request cũ chưa mang key.

Kiểm tra key trước và sau khi lấy khóa suất. Khi hai nội dung khác suất tranh cùng key, UNIQUE ở DB là lớp bảo vệ cuối: transaction thua rollback toàn bộ rồi facade mới đọc booking đã commit để quyết định replay/key-reused. Không đọc lại trong một transaction đã lỗi hoặc đánh dấu rollback-only. Lỗi thất bại không lưu phiếu sẽ không chiếm key; frontend vẫn có thể retry cùng key/body sau khi xác minh lại sơ đồ.

### 9.5 Khóa và chống trùng ghế

Ghi READ COMMITTED, cùng thứ tự với lịch: đọc context scalar → khóa phim → phòng → suất → (booking khi hủy) → các ghế ID tăng khi tạo. Sau khóa suất xác minh context nguồn không đổi; nếu khác rollback409 CONCURRENT_MODIFICATION. SELECT sau khi chờ khóa thấy dữ liệu đã commit, không dùng snapshot cũ. Movie/room/seat/showtime đã có giao thức khóa tương thích, không sửa hoặc tự hủy chúng để nhận booking.

Mọi ghế được kiểm tra trước INSERT. UNIQUE(showtime_id,active_seat_id) tiếp tục bảo đảm không có hai chiếm chỗ CONFIRMED dù có nhiều backend process; UNIQUE(booking_id,seat_id) riêng lẻ không đủ. Không dùng synchronized. Database còn bảo vệ ghế đúng phòng/suất qua FK ghép. Phiếu + tất cả chi tiết trong cùng @Transactional; violation/race rollback và trả409 an toàn. Map phải được tải lại sau lỗi ghế/giá/trạng thái.

Không thêm endpoint chèn thêm ghế vào booking cũ. Không ghi trực tiếp booking_status/active_seat_id; đó là cột trigger/generated quản lý. capacity_snapshot của suất và chính sách thống kê sức chứa chưa được thay đổi ở bước này.

### 9.6 Hủy và giải phóng

POST `/api/bookings/{id}/cancel` với `{"reason":"Khách đổi lựa chọn"}`, reason bắt buộc không trắng, tối đa500. **Chỉ ADMIN**, STAFF403 kể cả đơn mình tạo cho tới khi Kha duyệt D-02/D-09. Hủy mới phải trước startsAt, suất SCHEDULED. Không có hoàn tiền.

Khóa phim/phòng/suất rồi booking; CONFIRMED→CANCELLED, ghi người/thời điểm/lý do. Trigger hiện có đổi booking_status của **đúng các chi tiết có booking_id đang hủy**, sinh active_seat_id=null và giải phóng UNIQUE trong cùng transaction. Không DELETE và không UPDATE mọi dòng theo showtimeId+seatId.

Hủy lại booking đã CANCELLED trả200 cùng trạng thái/lịch sử, không viết lại lý do/người/thời gian, kể cả sau giờ chiếu. Vẫn cần ADMIN, CSRF và reason hợp lệ. Nếu ghế đã được booking mới đặt lại, hủy lặp đơn cũ không ảnh hưởng booking mới. CANCELLED không khôi phục; tìm booking cũ bằng mã/ID vẫn đủ ghế/giá.

### 9.7 Tra cứu, lỗi và hướng dẫn frontend

GET list nhận q (tìm chứa mã, tối đa200, %,_,! là ký tự thường), showtimeId dương, status CONFIRMED/CANCELLED, page0/size20 tối đa100; sort id/code/createdAt/totalAmount/status, direction asc/desc và id ASC phụ. Envelope items/total/page/size/totalPages; item giống BookingResponse. Ghế và suất được tải theo lô cho cả trang, không serialize association LAZY theo từng phiếu. by-code so sánh theo collation hiện tại, mã không tồn tại404; code dài quá40 là400.

| HTTP | code | Ý nghĩa |
|---|---|---|
|400|VALIDATION_FAILED|Body, key, ID trùng/rỗng/sai phòng, ageChecked, phân trang sai; fieldErrors theo trường|
|401|UNAUTHENTICATED|Không có phiên|
|403|FORBIDDEN/CSRF_INVALID|Thiếu quyền (gồm STAFF hủy) hoặc CSRF|
|404|RESOURCE_NOT_FOUND|Suất/phiếu/mã không tồn tại|
|409|SHOWTIME_NOT_BOOKABLE|Suất đã hủy/bắt đầu, không đặt hoặc hủy mới|
|409|RESOURCE_INACTIVE|Phim/phòng đã ngừng sử dụng|
|409|SEAT_UNAVAILABLE|Ghế vật lý INACTIVE|
|409|SEAT_ALREADY_BOOKED|Ghế vừa bị đặt, tải lại map|
|409|PRICE_CHANGED|Tải lại version/giá, yêu cầu xác nhận mới với key mới|
|409|IDEMPOTENCY_KEY_REUSED|Key cũ dùng cho nội dung khác|
|409|BOOKING_CONFLICT|DB chặn xung đột sau rollback, không có lưu một phần|
|409|CONCURRENT_MODIFICATION|Context/khóa thay đổi, tải lại dữ liệu|

Ví dụ lỗi ghế: envelope chung status409, code=SEAT_ALREADY_BOOKED, message="Có ghế vừa được người khác đặt; hãy tải lại sơ đồ.", path=/api/bookings, fieldErrors={}. Chưa trả thêm ID ghế xung đột trong envelope; frontend tải lại map, giữ các lựa chọn còn AVAILABLE và yêu cầu nhân viên xác nhận lại. Sau thành công/hủy/replay, đồng bộ booking và map theo response thật. SELECTED chỉ là trạng thái cục bộ, không hiển thị như đã giữ ghế. Lỗi mạng không có response thì retry cùng key/body để tra kết quả; không báo thất bại chắc chắn hoặc sinh key mới ngay.

Đề xuất còn chờ Kha duyệt: quyền tra cứu toàn rạp/đổi ca ở D-02, quyền hủy STAFF, cửa sổ hủy trước bắt đầu, giới hạn20 ghế, xác nhận tuổi, hai giá tường minh và cách chốt sức chứa. Không coi các đề xuất đó đã được duyệt chỉ vì đã có API. **Chưa kiểm chứng tích hợp giao diện thật của Đạt.**

### 9.8 Bằng chứng kiểm thử

84/84 test đạt trên DB mới `cinema_management_test_bookings_20261028_095639` (MySQL8.4.3/3306, schema+seed+V001). 15 Booking test và 69 regression cũ; map/giá/actor/validation, atomic rollback sau INSERT thật, snapshot, idempotency, cutoff và hủy lặp đều kiểm chứng HTTP/CSRF. Cùng ghế ở hai suất khác nhau vẫn đặt độc lập.

[Evidence đồng thời](../target/booking-concurrency.json) ghi7ca, mỗi ca có hai physical MySQL CONNECTION_ID khác nhau, duplicateActiveSeats=0 và partialBookings=0. Gồm cùng ghế, nhóm giao nhau, cùng key giống nội dung, cùng key khác suất, hủy/đặt lại, hủy suất/đặt, sửa giờ/đặt. Bằng chứng nâng cấp V001 trên history có sẵn ở [migration-check](../target/booking-migration-check.json), DB `cinema_management_test_booking_upgrade_20261028_100415`. Không tắt trigger/FK hoặc xóa lịch sử để dọn test.

Postman140request; chỉ JSON/body đã được kiểm tra, chưa chạy collection trong ứng dụng Postman. Chưa kiểm chứng frontend Đạt. Migration mới chỉ áp dụng DB thử; Kha cần kiểm tra và chạy V001 một lần trên DB của mình trước khi khởi động JAR mới.


## 10. Thống kê JdbcTemplate đã triển khai

GET `/api/admin/statistics/showtimes`, `/booked-seats`, `/occupancy`, `/cancellations`, chỉ ADMIN. Không có endpoint tổng quan mới. STAFF403 với phiên hợp lệ, ẩn danh401; GET không cần CSRF. Các route thay đổi dữ liệu cũ vẫn giữ CSRF.

Bắt buộc from/to dạng yyyy-MM-dd, bao gồm cả hai đầu, tối đa31ngày. SQL dùng [from00:00,to+1 00:00), giờ Việt Nam. Optional movieId/roomId dương, không tồn tại thì dữ liệu rỗng. page từ0, size1–100/default20. Thứ tự cố định ngày tăng, movieId tăng, occupancy startsAt/id tăng; không có sort SQL từ request. Invalidngày/thiếu/sai định dạng400 INVALID_REQUEST hoặc VALIDATION_FAILED; fieldErrors có ở validation service. Không trả PII, SQL hoặc stacktrace.

Envelope gồm from,to,timeZone="Asia/Ho_Chi_Minh",timeBasis,asOf có+07:00,items,total,page,size,totalPages. total là số dòng báo cáo, không phải số phiếu/ghế. Hai bảng theo ngày có dòng0 cho ngày trống; bảng phim/suất items=[] khi không dữ liệu.

| Endpoint | Item và định nghĩa |
|---|---|
| showtimes | date,totalShowtimes,scheduledShowtimes,cancelledShowtimes; đếm suất theo starts_at, tính cả hai trạng thái |
| booked-seats | movieId,movieTitle,activeBookings,bookedSeats,bookingValue; chỉ suất SCHEDULED và booking/ghế CONFIRMED; tổng giá trị mỗi phiếu đúng một lần |
| occupancy | showtimeId,movieId,movieTitle,roomId,roomCode,startsAt,bookedSeats,capacity,capacityBasis,occupancyPercent,anomaly; loại suất CANCELLED |
| cancellations | date,cancelledBookings,cancelledSeats,cancelledBookingValue; đếm sự kiện theo cancelled_at, gồm mọi trạng thái suất, không dùng created_at hoặc ngày chiếu thay ngày hủy |

Ba endpoint đầu timeBasis=SHOWTIME_START_DATE; cancellations=CANCELLATION_DATE. Suất qua nửa đêm thuộc ngày bắt đầu, không thuộc mọi ngày nó giao khoảng. Giá trị VND là giá trị booking, không phải doanh thu đã thu; giá trị hủy không phải tiền đã hoàn. Không JOIN genre trong aggregate. Ngày tạo booking chỉ là audit.

Mẫu số occupancy: có snapshot dùng SNAPSHOT; chưa bắt đầu và snapshotnull dùng ghế vật lý ACTIVE hiện tại với CURRENT_ACTIVE_SEATS_PROVISIONAL; đã bắt đầu mà thiếu snapshot trả capacity=null/percentage=null và MISSING_HISTORICAL_CAPACITY. Mẫu số0 trảpercentage=null. Kết quả làm tròn2 chữ số HALF_UP, không clamp100; bookedSeats>capacity đánh anomaly=true. Không ghi/backfill snapshot khi đọc; cơ chế chốt sức chứa lịch sử vẫn chờKha. Đạt hiển thị “Chưa có dữ liệu sức chứa lịch sử” hoặc “Không áp dụng” khi null, không tự đổi thành0%.

Ví dụ booked-seats:

```json
{"from":"2026-11-17","to":"2026-11-17","timeZone":"Asia/Ho_Chi_Minh","timeBasis":"SHOWTIME_START_DATE","asOf":"2026-10-28T10:50:00+07:00",
 "items":[{"movieId":51,"movieTitle":"Phim fixture A","activeBookings":2,"bookedSeats":3,"bookingValue":260000}],"total":1,"page":0,"size":20,"totalPages":1}
```

Đây là ví dụ fixture tổng hợp, không phải số liệu kinh doanh hoặc dự báo. Chi tiết công thức ở statistics.md, dữ liệu mong đợi/thực tế trong reports/evidence/statistics-evidence.json. 4test thống kê đạt cùng84regression cũ (tổng88), gồm150% không clamp, mẫu số0, thiếu lịch sử, biên00:00, phiếu nhiều ghế và đa thể loại. JAR cũng trả đúng260000 khi đọc fixture. Chưa benchmark dữ liệu lớn, chưa thêm chỉ mục/migration vì chưa có bằng chứng cần. Không khẳng định đã tích hợp frontend.

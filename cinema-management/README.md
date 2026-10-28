# Cinema Management — hệ thống quản lý rạp chiếu phim

## Frontend của Đạt cập nhật ngày 28/10/2026

Mở `http://127.0.0.1:8080/` hoặc `/login.html` sau khi backend khởi động. Đã có giao diện dùng API thật cho ADMIN/STAFF: danh mục, phòng/ghế, lịch, đặt/tra cứu/hủy và thống kê. Quản lý tài khoản chờ API của Kha. Xem [hướng dẫn frontend](docs/frontend/HUONG_DAN.md), [báo cáo và hạn chế](docs/frontend/BAO_CAO.md), [tệp thay đổi](docs/frontend/THAY_DOI.md). Bàn giao frontend trên frontend/dat và main theo yêu cầu của Đạt.

Các ghi chú “frontend trống/chưa kiểm chứng” trong lịch sử bàn giao backend bên dưới mô tả thời điểm bàn giao cũ. Bằng chứng frontend hiện tại ở `docs/frontend/evidence/`; không thay thế bằng chứng backend, nghiệm thu HTTPS hoặc thiết bị vật lý.

Backend cho một rạp có nhiều phòng, package `com.khadat.cinema`. Đã có mapping schema, xử lý request/lỗi, health, đăng nhập cookie BCrypt/CSRF và phân quyền ADMIN/STAFF. Đã có CRUD thể loại/phim, phòng, ghế vật lý và quản lý lịch chiếu; đã có đặt/hủy tại quầy theo mục14. Frontend HTML/CSS/JavaScript của Đạt chưa được sửa.

## 1. Phiên bản và công cụ

| Thành phần | Phiên bản dùng trong bản này |
|---|---|
| Java biên dịch (`--release`) | 21 |
| JDK thực tế chạy build/test | Eclipse Temurin 25.0.4.1; chưa chạy lại trên JDK 21 |
| Spring Boot | 3.5.16 |
| Spring Framework / Hibernate ORM | 6.2.19 / 6.6.53.Final, do Boot quản lý |
| Maven | 3.9.16 qua Maven Wrapper 3.3.4 |
| MySQL Server đã kiểm chứng | Laragon MySQL Community 8.4.3 |
| MySQL Connector/J | 9.7.0, do Boot quản lý |

Cần một **JDK** có javac, không chỉ JRE; Java 21 là mức bytecode của project. Spring Boot 3.5.16 hỗ trợ Java 17–25 theo [tài liệu hệ thống](https://docs.spring.io/spring-boot/3.5/system-requirements.html); vì project chọn release 21 nên dùng JDK 21–25. Chưa tuyên bố các runtime khác đã được test thực tế.

Cài/bật MySQL của Laragon; mysql CLI/HeidiSQL dùng khi nhập SQL thủ công. Có Internet cho lần đầu Wrapper/Maven tải công cụ và dependency; không cần cài Maven toàn hệ thống. Đặt `JAVA_HOME` vào thư mục JDK của chính máy bạn và thêm bin vào PATH; kiểm tra:

```powershell
java -version
javac -version
.\mvnw.cmd -version
```

macOS/Linux dùng `./mvnw` thay cho `.\mvnw.cmd` (nếu cần `chmod +x mvnw`). Wrapper ghim Maven và checksum SHA-256; không yêu cầu đường dẫn ổ D hoặc đường dẫn JDK trên máy Kha.

Dependency chỉ gồm Spring Web (HTTP/JSON), Spring Data JPA (Hibernate/JDBC/transaction), MySQL Connector runtime, Bean Validation và starter-test cho JUnit/MockMvc/Mockito. Bước xác thực bổ sung starter-security (Spring Security 6.5.11 do Boot quản lý) để quản lý phiên, BCrypt, CSRF và quyền; không thêm thư viện JWT hoặc cơ chế xác thực song song. Không thêm H2, Lombok, DevTools, Flyway hoặc Actuator.

## 2. Chuẩn bị database một lần

Đọc [database.md](docs/database.md), [business-rules.md](docs/business-rules.md), [screens.md](docs/screens.md). Các chính sách đang ghi “Đề xuất chờ Kha duyệt” vẫn chưa tự thành quyết định đã chốt chỉ vì đã có Entity.

1. Bật MySQL Laragon, xác định đúng cổng, tài khoản và phiên bản. Không dùng database shop cũ.
2. Kiểm tra database `cinema_management` có tồn tại/chứa dữ liệu hay chưa. Nếu đã có schema, đối chiếu, không chạy lại baseline hoặc xóa để sửa lỗi.
3. Nếu chưa có, trong phiên SQL quản trị tạo database mới trống:

```sql
CREATE DATABASE cinema_management CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
```

4. Từ thư mục chứa pom.xml, mở mysql CLI (nhập mật khẩu tại prompt, không viết giá trị sau `--password`):

```text
mysql --host=127.0.0.1 --port=3306 --user=<tai_khoan_sql> --password --default-character-set=utf8mb4 cinema_management
mysql> SOURCE database/schema.sql;
mysql> SOURCE database/seed.sql;
mysql> SOURCE database/migrations/V001__booking_idempotency.sql;
```

Có thể mở/chạy các file trong HeidiSQL, chọn đúng database trống và hỗ trợ DELIMITER/trigger. Chạy một lần, không `--force`. Baseline có 11 bảng/7 trigger; V001 thêm metadata idempotency và một trigger (tổng8). DB đã có baseline chỉ chạy V001 một lần, không nhập lại baseline/seed. Seed là dữ liệu minh họa, không có tài khoản đăng nhập ứng dụng hay mật khẩu thuần.

Backend luôn `spring.sql.init.mode=never` và `ddl-auto=validate`, **không chạy schema/seed, tạo bảng hoặc sửa cấu trúc khi khởi động**. Không dùng `create`, `create-drop` hay `update` để chữa mapping. Hibernate validate kiểm tra bảng/cột/kiểu cần cho mapping, không thay thế kiểm tra tất cả FK/UNIQUE/CHECK/trigger bằng [verify.sql](database/verify.sql).

Ở lượt dựng nền tảng chỉ tạo DB thử `cinema_management_test_backend_20261027` trên Laragon 3306; chưa tạo DB phát triển `cinema_management`. Các DB khác giữ nguyên.

## 3. Biến môi trường và cấu hình mẫu

| Biến | Mặc định local | Ý nghĩa |
|---|---|---|
| DB_HOST | 127.0.0.1 | Máy MySQL |
| DB_PORT | 3306 | Cổng MySQL |
| DB_NAME | cinema_management | Database đã nhập schema |
| DB_USERNAME | root | Tài khoản SQL; khi triển khai hãy cấp quyền tối thiểu cho tài khoản riêng |
| DB_PASSWORD | rỗng | Mật khẩu SQL lấy từ môi trường, không ghi vào Git |
| SERVER_ADDRESS | 127.0.0.1 | Chỉ lắng nghe local theo mặc định |
| SERVER_PORT | 8080 | Cổng HTTP |
| SESSION_COOKIE_SECURE | false | Đặt true khi triển khai HTTPS; HTTP localhost giữ false |
| CINEMA_MYSQL_IT | không đặt | Đặt `true` chỉ khi chạy integration test trên DB thử |

Ví dụ PowerShell trên máy Kha hoặc Đạt (chạy từ thư mục pom.xml):

```powershell
$env:DB_HOST = '127.0.0.1'
$env:DB_PORT = '3306'
$env:DB_NAME = 'cinema_management'
$env:DB_USERNAME = 'tai_khoan_sql_cua_ban'
$secret = Read-Host 'MySQL password' -AsSecureString
$env:DB_PASSWORD = [System.Net.NetworkCredential]::new('', $secret).Password
$env:SERVER_PORT = '8080'
.\mvnw.cmd spring-boot:run
# Dừng bằng Ctrl+C; xóa biến mật khẩu khỏi phiên khi xong:
Remove-Item Env:DB_PASSWORD -ErrorAction SilentlyContinue
```

Nếu tài khoản local thật sự không có mật khẩu, có thể đặt `$env:DB_PASSWORD=''`. Không áp dụng giả định này cho máy khác. Biến môi trường là cấu hình của phiên đang chạy; IDE cần khai báo riêng trong run configuration. File `.env` không tự được Spring Boot nạp và không có thư viện tự nạp .env trong project.

[application-local.example.properties](src/main/resources/application-local.example.properties) là mẫu, **không tự được nạp**. Nếu muốn dùng profile, copy thành `application-local.properties`, chỉ chỉnh bản local đã được .gitignore, chạy JAR với `--spring.profiles.active=local`. Ưu tiên giữ mật khẩu trong biến môi trường. Không gửi bản local, .env hay cấu hình chứa mật khẩu cho Đạt.

Lịch và DATETIME tuân theo giờ Việt Nam; JDBC dùng `connectionTimeZone=%2B07:00` và ép session về +07:00, không cần cài bảng tên múi giờ MySQL. `Clock`/timestamp lỗi dùng Asia/Ho_Chi_Minh. Không trộn dữ liệu UTC vào các cột DATETIME đã quy ước giờ địa phương.

## 4. Build, chạy và kiểm tra

```powershell
.\mvnw.cmd test
.\mvnw.cmd package
java -jar target/cinema-management-0.0.1-SNAPSHOT.jar
```

Hoặc `.\mvnw.cmd spring-boot:run`. Cấu hình DB môi trường phải nằm trong chính phiên chạy lệnh. Khi startup thành công, log có `Initialized JPA EntityManagerFactory` và `Started CinemaApplication`; không bỏ validate để ép ứng dụng chạy nếu schema sai.

Kiểm tra:

```powershell
Invoke-RestMethod http://127.0.0.1:8080/api/health
```

Kỳ vọng HTTP 200:

```json
{"status":"UP","database":"UP"}
```

Endpoint thực hiện `SELECT COUNT(*) FROM roles` qua kết nối MySQL thật; không trả số liệu nội bộ, URL kết nối hoặc credential. Nếu database mất kết nối **sau khi app đã chạy**, trả HTTP 503 với JSON lỗi `DATABASE_UNAVAILABLE`. Nếu database không truy cập được hoặc schema không khớp **ngay lúc startup**, Hibernate validate làm ứng dụng dừng; chưa có HTTP listener khỏe để trả 503. Không có API nhận SQL từ người gọi.

API đã triển khai: `GET /api/health`, `GET /api/auth/csrf`, `POST /api/auth/login`, `GET /api/auth/me`, `POST /api/auth/logout`. Health/CSRF công khai; `/me` cần phiên ADMIN/STAFF. Login/logout cần CSRF. Hợp đồng request/response và ma trận quyền: [docs/api.md](docs/api.md). CRUD thể loại/phim đã có ở `/api/admin/genres` và `/api/admin/movies`; phòng/ghế ở `/api/admin/rooms` và các route ghế lồng trong phòng (chỉ ADMIN). Lịch ADMIN ở `/api/admin/showtimes`, đọc ADMIN/STAFF ở `/api/showtimes`. Xem mục 6–8 trong docs/api.md. Đặt chỗ tại quầy ở mục14.

## 5. Mapping JPA và cấu trúc backend

```text
com.khadat.cinema
  CinemaApplication
  config/ApplicationConfig                 Clock + BCryptPasswordEncoder(12)
  config/SecurityConfig                    phiên, CSRF, quyền theo method/path
  config/CinemaPrincipal, SecurityErrorWriter
  config/LocalAccountRunner                chỉ profile local-accounts
  controller/AuthController               csrf/login/me; logout do filter xử lý
  service/AuthService                     đọc users + roles từ MySQL
  service/LocalAccountProvisioningService  cấp tài khoản local có chủ đích
  repository/UserRepository, RoleRepository, UserRoleRepository
  dto/auth/                               LoginRequest, CurrentUserResponse, CsrfResponse
  controller/HealthController              GET /api/health
  controller/ApiErrorController            fallback JSON /error
  service/HealthService
  repository/DatabaseProbeRepository       truy vấn cố định bằng JdbcTemplate
  dto/HealthResponse, ApiError
  entity/                                 11 Entity + UserRoleId/MovieGenreId
  exception/                              advice và exception dùng chung
  controller/GenreController, MovieController
  service/GenreService, MovieService, CatalogQuery
  repository/GenreRepository, MovieRepository, MovieGenreRepository
  dto/genre/, dto/movie/, dto/PageResponse
  mapper/GenreMapper, MovieMapper          Entity -> DTO, không trả Entity
```

Các file rỗng có từ cây ban đầu vẫn là giữ chỗ, không phải chức năng đã hoàn thành. Không tạo thêm controller/service/repository nghiệp vụ rỗng.

- Hai bảng nối dùng Entity với `@EmbeddedId`, khóa ghép có equals/hashCode; không tự dùng ManyToMany song song.
- FK có cột scalar làm chủ ghi; các ManyToOne là LAZY, read-only trên chính cột đó, kể cả FK ghép. Khi tạo Entity sau này, gán các ID scalar; không gán liên kết read-only rồi mong Hibernate tự ghi ID. Không có cascade REMOVE/ALL, orphanRemoval hoặc quan hệ hai chiều tải cả đồ thị.
- API trả DTO, không trả Entity. Entity không có toString/equals đệ quy qua quan hệ; `open-in-view=false`. Mapper nghiệp vụ sẽ thêm khi có chức năng cần dùng.
- Tiền là BigDecimal, ngày giờ là LocalDateTime theo quy ước SQL; SMALLINT/TINYINT unsigned được khai báo kiểu SQL tương ứng. ID Java là Long, ứng dụng chỉ hỗ trợ miền ID dương <= Long.MAX_VALUE dù BIGINT UNSIGNED của DB có miền lớn hơn.
- `created_at`, `updated_at`, `booking_status`, `active_seat_id` do DB sinh/quản lý có `insertable=false, updatable=false` và Hibernate Generated. Không có setter cho cột generated. Khi trigger hủy cha làm đổi chi tiết đang có trong persistence context, cần `refresh`/clear rồi đọc lại chi tiết; không dùng đối tượng đang cache để kết luận ghế vẫn hiệu lực.
- Snapshot/tổng đơn không update bởi JPA; tính toàn bộ trước INSERT trong giao dịch sau này. Hiện chưa có code đặt/hủy/tính giá; bài test round-trip chỉ kiểm chứng mapping và trigger, rollback dữ liệu thử.
- price_version chưa dùng `@Version`: schema hiện quy định service tăng khi đổi giá, không tự thay đổi chính sách tại bước nền tảng. Trạng thái map String đúng VARCHAR/CHECK hiện có; việc duyệt chính sách vẫn ở tài liệu nghiệp vụ.

## 6. Kiểm thử và kết quả thực tế

Test thường không cần MySQL:

```powershell
.\mvnw.cmd test
```

Integration test phải dùng một database **thử riêng**, đã nhập schema và seed, tên bắt đầu `cinema_management_test_`. Không đổi tên DB thật để vượt guard. Có thể tạo DB mới rồi SOURCE schema/seed như mục 2. Không cần chạy runner database/test_database.py trước vì runner đó giữ dữ liệu race, có thể chiếm ghế fixture của bài test JPA.

```powershell
$env:DB_NAME = 'cinema_management_test_backend_may_dat'
# Đặt DB_HOST/DB_PORT/DB_USERNAME/DB_PASSWORD đúng kết nối thử trên máy bạn.
$env:CINEMA_MYSQL_IT = 'true'
.\mvnw.cmd -Pmysql-it verify
Remove-Item Env:CINEMA_MYSQL_IT -ErrorAction SilentlyContinue
```

Test MySQL chỉ chạy khi bật cả Maven profile và biến opt-in; kiểm tra prefix database trước ghi. Không tự tạo/xóa bảng. Fixture JPA trong giao dịch rollback; AUTO_INCREMENT có thể tăng dù rollback. Failsafe bị skip nếu không opt-in **không được báo là integration test đã đạt**.

Kết quả ngày 27/10/2026:

- Build/package và `-Pmysql-it verify` thành công, JDK 25.0.4.1, target Java 21.
- **88 test đạt, không lỗi/không skip**: 12 nền tảng MockMvc, 5 MySQL nền tảng, 14 Security, 13 Catalog, 12 RoomSeat, 13 Showtime, 15 Booking và 4 Statistics.
- MySQL Laragon 8.4.3 cổng 3306, DB riêng `cinema_management_test_backend_20261027`; Hibernate validate thành công. Không sửa SQL/schema hoặc dữ liệu DB khác.
- Security test chạy HTTP/Tomcat thật, cookie thật và token lấy từ `/csrf`: đúng/sai mật khẩu, email không tồn tại, INACTIVE, 401 ẩn danh, STAFF có CSRF hợp lệ bị 403, ADMIN tương ứng 200, thiếu/sai CSRF, đổi session ID, token cũ sau login, me, logout và phát lại cookie cũ, response không có mật khẩu/hash. Controller probe users chỉ nằm trong src/test; Catalog test dùng controller phim/thể loại thật.
- Fixture tài khoản có email/mật khẩu ngẫu nhiên, BCrypt cost 12 và được dọn sau test. Test JPA rollback, AUTO_INCREMENT có thể tăng. Chưa có tài khoản mặc định dùng chung.
- 400/409/500/415 nền tảng được test qua fixture; không công bố là API nghiệp vụ. Kiểm tra mất DB/503 trên instance DB thử riêng ở bước nền tảng vẫn được giữ; không dừng MySQL chính.
- JAR đóng gói đã chạy HTTP trên cổng thử riêng: health 200, ADMIN/STAFF login/me/logout thành công, ADMIN qua cổng quyền tới route phim chưa triển khai nhận 404, STAFF cùng request hợp lệ nhận 403, me sau logout 401. Controller test không có trong JAR. Tiến trình thử đã dừng; không tác động app cổng 8080.
- Postman collection có 140 request, đã kiểm tra JSON và credential ban đầu để trống; chưa chạy collection trong ứng dụng Postman.
- Profile local-accounts đã tạo ADMIN và STAFF trên DB thử, hash BCrypt/role đúng, tiến trình non-web thoát, không in credential. Chạy lại bị từ chối, hash cũ không đổi; đã dọn đúng hai tài khoản CLI thử.
- **Chưa kiểm chứng trên máy Đạt**, JDK 21 runtime hoặc môi trường HTTPS. Không dùng báo cáo nền tảng cũ về 404/405 để suy ra response hiện tại: Security chạy trước MVC, route ngoài danh sách có thể là 401/403.

## 7. Xử lý lỗi thường gặp

| Biểu hiện | Cách xử lý |
|---|---|
| java/javac không tìm thấy hoặc release 21 không hỗ trợ | Cài JDK 21–25, sửa JAVA_HOME/PATH của máy bạn; kiểm tra mvnw -version đang dùng JDK nào |
| PKIX path building failed khi tải Maven/dependency | Kiểm tra ngày giờ, proxy và CA tin cậy của tổ chức; trên Windows có thể dùng MAVEN_OPTS `-Djavax.net.ssl.trustStoreType=Windows-ROOT -Djavax.net.ssl.trustStore=NONE` nếu CA đã tin cậy trong Windows. Không tắt kiểm tra TLS hoặc đổi repo sang HTTP |
| Connection refused / Communications link failure | Bật MySQL, kiểm tra DB_HOST/DB_PORT và kết nối bằng HeidiSQL/mysql; không đổi schema |
| Access denied | Kiểm tra username/password, quyền và host tài khoản; không paste mật khẩu vào log/chat/Git |
| Unknown database | Kiểm tra DB_NAME; tạo database mới và nhập SQL thủ công theo mục 2 nếu chưa có, không ghi đè database khác |
| Schema-validation: missing table/column/wrong type | Đối chiếu schema.sql và DB đang chọn; không đổi ddl-auto=update. DB cũ khác thiết kế phải có migration được duyệt |
| Unknown or incorrect time zone | Dùng cấu hình JDBC +07:00 đã cung cấp, không cần import timezone tables vào MySQL để chạy local |
| Port 8080 already in use | Chọn SERVER_PORT khác hoặc xác định tiến trình đang dùng; không tự dừng ứng dụng khác |
| Health 503 sau startup | Kiểm tra database/quyền đọc roles; HTTP chỉ trả thông báo an toàn; nguyên nhân kỹ thuật xem log phía server |
| 401 UNAUTHENTICATED | Lấy CSRF, login, giữ cookie; kiểm tra hết phiên 30 phút hoặc server vừa khởi động lại |
| 403 CSRF_INVALID | Lấy token cùng cookie hiện tại; lấy lại sau login/logout; không tắt CSRF |
| 403 FORBIDDEN | Kiểm tra vai trò và đúng method/path trong docs/api.md; không mở quyền rộng để chữa lỗi |
| ADMIN gọi module chưa làm (ví dụ users) nhận 404 | Chưa có controller nghiệp vụ đó; phim/genre đã có CRUD ở hai route trong docs/api.md |

## 8. Tạo ADMIN/STAFF riêng cho local

Không có API đăng ký hoặc mật khẩu mặc định. Seed không chứa tài khoản đăng nhập. Profile **local-accounts** chỉ được bật chủ động, chạy non-web một lần, thêm tài khoản ACTIVE với BCrypt và gắn đúng vai trò đã seed; không ghi đè email/username tồn tại. Không dùng profile này trên dữ liệu thật hoặc khi chạy ứng dụng thường.

Chuẩn bị schema/seed ở DB local hoặc DB thử trước, đặt biến DB ở mục 3, build `mvnw package`. Từ cùng phiên PowerShell:

```powershell
$env:LOCAL_ACCOUNT_CONFIRM = 'LOCAL_ONLY'
$env:LOCAL_ACCOUNT_EMAIL = Read-Host 'Email tai khoan local'
$env:LOCAL_ACCOUNT_USERNAME = Read-Host 'Username rieng (3-50 ky tu A-Z a-z 0-9 . _ -)'
$env:LOCAL_ACCOUNT_NAME = Read-Host 'Ten hien thi (1-100 ky tu)'
$env:LOCAL_ACCOUNT_ROLE = Read-Host 'ADMIN hoac STAFF'
$accountSecret = Read-Host 'Mat khau rieng (12-72 byte UTF-8)' -AsSecureString
$env:LOCAL_ACCOUNT_PASSWORD = [System.Net.NetworkCredential]::new('', $accountSecret).Password
try {
    java -jar target/cinema-management-0.0.1-SNAPSHOT.jar --spring.profiles.active=local-accounts
    if ($LASTEXITCODE -ne 0) { throw 'Khong tao duoc tai khoan; kiem tra loi, khong ghi de tai khoan cu.' }
} finally {
    'LOCAL_ACCOUNT_CONFIRM','LOCAL_ACCOUNT_EMAIL','LOCAL_ACCOUNT_USERNAME','LOCAL_ACCOUNT_NAME','LOCAL_ACCOUNT_ROLE','LOCAL_ACCOUNT_PASSWORD' | ForEach-Object {
        Remove-Item -LiteralPath ('Env:' + $_) -ErrorAction SilentlyContinue
    }
    Remove-Variable accountSecret -ErrorAction SilentlyContinue
}
```

Chạy hai lần với email/username riêng để tạo một ADMIN và một STAFF; mỗi người tự chọn mật khẩu riêng, không nhập mật khẩu trong câu lệnh hoặc lưu vào file. Thành công log `Local account created. Credentials were not printed...`, tiến trình thoát; không mở cổng HTTP. Trùng tài khoản, thiếu role hoặc dữ liệu sai: dừng lỗi, transaction không thêm/đổi tài khoản. Không dùng việc gọi lại để reset mật khẩu. Khi chạy ứng dụng thường bỏ profile này.

Kha tự nhập credential vào biến local của Postman. Đạt có thể tự tạo tài khoản trên DB riêng bằng cách này; nếu dùng DB chung, gửi credential riêng qua kênh riêng của nhóm, không đưa vào README, collection xuất, Git hoặc ảnh minh chứng.

## 9. Bàn giao cho Đạt và điểm chờ duyệt

Đạt lấy đủ project/Wrapper, chuẩn bị JDK/MySQL, nhập SQL vào DB riêng và đặt môi trường như trên. [API](docs/api.md) mô tả đầy đủ cookie và CSRF, [Postman](docs/postman/cinema.postman_collection.json) có luồng ADMIN/STAFF và ca lỗi. Đặt `adminEmail/adminPassword/staffEmail/staffPassword` trong environment riêng của Postman; collection chia sẻ để trống credential. Giữ cookie jar bật, chạy đúng thứ tự. ADMIN có thể CRUD phim/thể loại thật; STAFF đọc/ghi các route này đều 403. Module users vẫn 404 khi ADMIN qua cổng quyền; controller probe users chỉ có trong src/test.

Frontend lấy CSRF → login → lấy CSRF mới → me → thao tác có cookie/token → logout. Cookie HttpOnly, SameSite=Lax; `SESSION_COOKIE_SECURE=true` khi dùng HTTPS. Không JWT hoặc CORS rộng, không sửa frontend ở lượt này.

**Đề xuất chờ Kha duyệt:** quyền/điều kiện hủy đặt chỗ và các D-* trong business-rules/database; cơ chế quản trị tài khoản, bảo vệ ADMIN cuối cùng, thu hồi ngay phiên đang mở khi khóa/đổi quyền sẽ quyết định trước module tài khoản. Hiện INACTIVE bị chặn lúc đăng nhập mới; session lưu identity tại thời điểm login. Chưa cấp thêm quyền hủy.

Chưa commit/push. Chưa có Git repository/nhánh tại workspace, không tự khởi tạo Git. Không đổi schema, SQL hoặc frontend. Chưa triển khai module lịch chiếu/đặt chỗ; ghế hiện là sơ đồ vật lý.

## 10. Tệp thay đổi ở bước xác thực

Các đường dẫn Java dưới `src/main/java/com/khadat/cinema/`:

- `config/ApplicationConfig.java`, `SecurityConfig.java`, `CinemaPrincipal.java`, `SecurityErrorWriter.java`, `LocalAccountRunner.java`.
- `controller/AuthController.java`, `service/AuthService.java`, `service/LocalAccountProvisioningService.java`.
- `repository/UserRepository.java`, `RoleRepository.java`, `UserRoleRepository.java`.
- `dto/auth/LoginRequest.java`, `CurrentUserResponse.java`, `CsrfResponse.java`.
- `src/main/resources/application.properties`, `application-local-accounts.properties`; `pom.xml` thêm duy nhất starter-security.
- `src/test/java/com/khadat/cinema/integration/SecurityMySqlIT.java`, `FoundationMySqlIT.java` (route ẩn danh nay bị Security trả 401).
- `README.md`, `docs/api.md`, `docs/postman/cinema.postman_collection.json`.

`target/` chỉ chứa sản phẩm build/báo cáo kiểm thử, không bàn giao như mã nguồn. Mapping entity và các file nền tảng giữ nguyên.


## 11. Bàn giao CRUD thể loại/phim (bước hiện tại)

Không cần dependency, cấu hình backend hay migration mới. Tận dụng entity/schema và hai nhóm URL đã được Security bảo vệ; không có hệ thống CRUD song song.

API thật: POST/GET danh sách tại `/api/admin/genres`, `/api/admin/movies`; GET/PUT/DELETE từng `/{id}`. ADMIN có quyền; STAFF không đọc/ghi hai nhóm quản trị này. Tạo 201 có Location, đọc/sửa 200, xóa 204; validation 400, chưa đăng nhập 401, quyền/CSRF 403, không tồn tại 404, liên kết/trùng dữ liệu 409.

[Hợp đồng chi tiết cho Đạt](docs/api.md) mục 6 quy định toàn bộ request/response, PUT, null, genreIds, phân trang, bộ lọc và mã lỗi. PUT yêu cầu gửi lại toàn bộ trường bắt buộc; trường tùy chọn bị bỏ qua được xóa thành null. IDs thể loại trùng được loại bỏ; danh sách rỗng bị từ chối; thể loại không tồn tại trả 400 UNKNOWN_GENRES và rollback cả phim/liên kết. Phim trùng tên được phép, mã phim duy nhất; genre không có cột code/status.

Bảo vệ: genre đang dùng không xóa; phim có bất kỳ suất nào không xóa. Chưa duyệt D-04 nên tạm khóa thời lượng/tuổi với mọi lịch sử suất, chặt hơn BR-005 dự kiến; không tự đổi giờ kết thúc/snapshot. Phim còn suất chưa hủy chưa kết thúc không được INACTIVE. Đây là phương án bảo vệ chờ Kha duyệt, không tự coi các đề xuất cũ đã chốt. Tuổi 0–18 giữ đúng CHECK của schema; yêu cầu ít nhất một genre, quy ước mã và giới hạn request được ghi rõ trong API để Kha duyệt tiếp.

Giao dịch ghi READ COMMITTED, khóa phim trước genre theo ID; không cascade REMOVE. Module lịch sau phải tuân khóa phim/phòng và kiểm tra lại trạng thái theo docs/database.md. Phân trang phim dùng EXISTS + tải thể loại bằng projection theo lô; test thống kê Hibernate xác nhận tối đa 3 SELECT cho một trang có nhiều genre (trang/count/genre), không N+1 hoặc lặp phim.

Kiểm thử ngày 27/10/2026: `mvnw -Pmysql-it verify` đạt **44/44** trên Laragon MySQL 8.4.3, cổng 3306, DB mới riêng **cinema_management_test_catalog_20261027_225624**. Baseline schema/seed hiện có nhập thành công; Hibernate validate thành công, không đổi schema. 13 test Catalog dùng HTTP/cookie/CSRF thật bao phủ CRUD, nhiều genre và thay liên kết, rollback ID sai, Unicode/collation trùng tên, mã trùng, validation/URL/số lẻ, 404, lịch sử suất cũ/hủy/tương lai, tìm/lọc/phân trang, số truy vấn và ADMIN/STAFF. 31 test nền tảng/xác thực tiếp tục đạt, gồm login/me/logout. Fixture Catalog và tài khoản thử được dọn; giữ lại seed minh họa. Không chạy ca lỗi trên DB thật/shop.

Đạt có sẵn ba phim/ba thể loại minh họa trong seed để dựng danh sách. Chạy thư mục **05 Genres then movies CRUD** trong collection để tạo hai thể loại và phim riêng, đọc/sửa/filter rồi xóa đúng dữ liệu vừa tạo; không xóa seed. Giữ cookie jar bật, nhập credential trong environment riêng, lấy CSRF sau login. Collection có 140 request, đã kiểm tra JSON, chưa chạy trong ứng dụng Postman. **Chưa kiểm chứng tích hợp giao diện thật của Đạt.**

Tệp tạo/điền ở bước CRUD (25 tệp; đường dẫn Java tương đối với `src/main/java/com/khadat/cinema/`):

| Nhóm | Tệp |
|---|---|
| Controller | `controller/GenreController.java`, `controller/MovieController.java` |
| Service | `service/GenreService.java`, `service/MovieService.java`, `service/CatalogQuery.java` |
| Repository | `repository/GenreRepository.java`, `repository/MovieRepository.java`, `repository/MovieGenreRepository.java` |
| Mapper | `mapper/GenreMapper.java`, `mapper/MovieMapper.java` |
| DTO | `dto/PageResponse.java`, `dto/genre/GenreRequest.java`, `dto/genre/GenreResponse.java`, `dto/movie/MovieRequest.java`, `dto/movie/MovieResponse.java`, `dto/movie/MovieGenreView.java`, `dto/movie/StrictIntegerDeserializer.java`, `dto/movie/StrictLongDeserializer.java` |
| Lỗi | `exception/FieldValidationException.java`, `exception/GlobalExceptionHandler.java` |
| Test | `src/test/java/com/khadat/cinema/integration/CatalogMySqlIT.java`, `src/test/java/com/khadat/cinema/integration/SecurityMySqlIT.java` (chuyển probe sang users vì movies đã có API thật) |
| Tài liệu | `README.md`, `docs/api.md`, `docs/postman/cinema.postman_collection.json` |

Giữ nguyên AGENTS (không tìm thấy), entity, schema/seed/migration, pom/config và HTML/CSS/JavaScript. Workspace chưa có Git repository nên không có nhánh/status để đối chiếu diff; chưa init, commit hoặc push.


## 12. Bàn giao phòng chiếu và ghế vật lý (bước hiện tại)

Đã triển khai CRUD `/api/admin/rooms`, GET/POST `/api/admin/rooms/{roomId}/seats`, PUT/DELETE ghế theo `/{seatId}` và POST `.../seats/bulk`. Chỉ ADMIN, giữ phiên cookie/CSRF và lỗi chung; không mở quyền STAFF ở các route quản trị. SecurityConfig chỉ bổ sung các matcher cụ thể cho route nested mới. Không thêm dependency/cấu hình kết nối/schema/migration; không sửa frontend, phim/thể loại hoặc đăng nhập.

[API mục 7](docs/api.md) có request/response, hai JSON sơ đồ, mã lỗi và quy ước khóa. Phòng không có cột capacity: trả seatCount (mọi ghế), activeSeatCount (ghế ACTIVE), không coi là số ghế trống theo suất. Ghế STANDARD/VIP, ACTIVE/INACTIVE; hỏng/ngừng sử dụng cùng dùng INACTIVE vì schema chưa có lý do riêng. Mã ghế server sinh từ hàng uppercase + số; sơ đồ sắp hàng A…Z, AA… rồi số nguyên, A2 trước A10.

**Đề xuất chờ Kha duyệt D-05:** hàng 1–4 chữ A–Z, giới hạn bulk 26 hàng/50 ghế mỗi hàng/tổng500, phòng tối đa500 ghế khi thêm; chỉ bulk phòng trống chưa có suất. Không cascade/reset. Xóa phòng chỉ khi không ghế và không suất. Khóa cấu trúc ghế sau bất kỳ suất nào. Chặn đổi trạng thái phòng/ghế khi còn suất chưa hủy chưa kết thúc (chặt hơn đề xuất BR-008), kể cả chưa có booking. Nếu chỉ còn lịch sử đã kết thúc/hủy, cho đổi trạng thái vật lý nhưng giữ giá/snapshot/lịch sử. Kha cần xác nhận có muốn phân biệt lý do hỏng và có nới chính sách đối với suất tương lai hay không trước khi thiết kế module lịch.

Ghi phòng/ghế dùng READ COMMITTED, khóa dòng phòng trước kiểm tra và ghi cả khi phòng đang trống; UNIQUE MySQL bảo vệ mã/vị trí. Module lịch/đặt ghế sau phải phối hợp theo thứ tự khóa phim → phòng → suất → đơn → ghế và kiểm tra lại trạng thái sau khóa. Không có API theo suất hoặc nghiệp vụ đặt chỗ mới.

Kết quả `mvnw -Pmysql-it verify`: **56/56 đạt** trên MySQL Laragon 8.4.3 cổng3306, DB mới riêng `cinema_management_test_rooms_20261027_231404`. Hibernate validate thành công; nhập schema/seed nguyên bản. 12 test RoomSeat dùng HTTP/Tomcat, cookie/token thật: CRUD/mã trùng, sơ đồ hai phòng cùng A1, UNIQUE trực tiếp ở DB, sai hàng/số/loại/trạng thái, A2 trước A10, sai roomId, bulk/replay/giới hạn, cạnh tranh hai request (201 và409), và lỗi test-only sau INSERT/flush đầu tiên rollback toàn bộ. Fixture đặt chỗ tương lai/quá khứ xác nhận chặn sửa/xóa trái quy tắc, không đổi giá/snapshot hoặc tự hủy đơn. 44 test xác thực/thể loại/phim/nền tảng cũ tiếp tục đạt.

Tài khoản đăng nhập test được dọn. **Hai phòng fixture có booking lịch sử cùng một user INACTIVE không có hash được giữ trong DB thử**, vì trigger cấm xóa lịch sử; không tắt FK/trigger để dọn. Không có credential dùng chung hoặc dữ liệu thật. Các fixture phòng không có lịch sử đã được dọn. Không tạo DB phát triển hoặc sửa/xóa DB shop/DB khác.

Postman có 140 request, thư mục **06 Rooms and physical seats** tạo hai phòng riêng, đọc/tạo bulk/sửa/xóa đúng các ghế/phòng vừa tạo; không reset seed. Các biến credential vẫn trống trong file chia sẻ. Đã kiểm tra cấu trúc JSON/body; **chưa chạy collection trong ứng dụng Postman, chưa kiểm chứng tích hợp frontend thật của Đạt**. Sau build, chạy lại backend của bạn để nạp API mới; build không cập nhật một tiến trình JAR đang chạy.

19 tệp tạo/điền/sửa ở bước này (đường dẫn Java dưới `src/main/java/com/khadat/cinema/`):

| Nhóm | Tệp |
|---|---|
| Controller | `controller/RoomController.java`, `controller/SeatController.java` |
| Service | `service/RoomService.java`, `service/SeatService.java` |
| Repository | `repository/RoomRepository.java`, `repository/SeatRepository.java` |
| Mapper | `mapper/RoomMapper.java`, `mapper/SeatMapper.java` |
| DTO phòng | `dto/room/RoomRequest.java`, `dto/room/RoomResponse.java` |
| DTO ghế | `dto/seat/SeatRequest.java`, `dto/seat/SeatResponse.java`, `dto/seat/BulkSeatRequest.java`, `dto/seat/SeatMapResponse.java` |
| Phân quyền | `config/SecurityConfig.java` |
| Test | `src/test/java/com/khadat/cinema/integration/RoomSeatMySqlIT.java` |
| Tài liệu | `README.md`, `docs/api.md`, `docs/postman/cinema.postman_collection.json` |

Chưa commit/push. Workspace chưa có Git repository/nhánh; không khởi tạo lại project/Git. Các sản phẩm và log trong target/ là artifact kiểm thử được gitignore.


## 13. Bàn giao lịch chiếu và HATEOAS (bước hiện tại)

Đã bổ sung duy nhất dependency `spring-boot-starter-hateoas` (Spring HATEOAS 2.5.3 do Boot quản lý) theo yêu cầu để dùng EntityModel/Link. Không đổi phiên bản Java/Boot, cấu hình MySQL, schema, entity hoặc các module phim/phòng/ghế. Các module hiện có đã khóa phim/phòng nên phối hợp được với lịch mà không cần sửa nghiệp vụ cũ.

ADMIN quản lý `/api/admin/showtimes`: POST/GET list, GET/PUT/DELETE từng `/{id}`, POST `/{id}/cancel`. ADMIN và STAFF đọc list/detail ở `/api/showtimes`. Tạo201 có Location, đọc/sửa/hủy200, xóa204. Giữ phiên cookie/CSRF, JSON lỗi chung; thêm matcher GET quản trị và POST cancel cụ thể. Không mở quyền STAFF quản trị, không có API ghế theo suất hoặc đặt chỗ.

Body bắt buộc movieId/roomId/startsAt/standardPrice/vipPrice; startsAt ISO offset đúng +07:00, chính xác phút, tương lai. Giữ nguyên offset tại DTO để không bị Jackson đổi UTC trước validation. Backend khóa rồi đọc thời lượng, lưu duration/minimumAge snapshot và ends_at. Hai giá VND nguyên theo schema, VIP không thấp hơn thường, không có hệ số mới. PUT thay đủ năm trường, không gửi endsAt/status; giá đổi tăng priceVersion. Trang size1–100, day/filter theo **ngày bắt đầu** hoặc khoảng startsAt [from,to) tối đa31ngày. Qua nửa đêm vẫn giữ cả ngày bắt đầu/kết thúc và kiểm tra trùng trên toàn khoảng.

[API mục8](docs/api.md) ghi công thức trùng hai bất đẳng thức, biên đúng15phút, kiểu dữ liệu/HATEOAS, quyền và JSON để Đạt dựng lịch. Detail dùng EntityModel giữ trường DTO ở root + `_links`; list giữ PageResponse. ADMIN có self/collection/movie/room và chỉ có link thao tác khi hợp lệ; STAFF chỉ có self/collection tới route đọc mà STAFF được phép. Không có link tới API chưa làm. Không N+1: lấy trang/count rồi một projection join phim/phòng/booking-count cho cả trang.

**Đề xuất chờ Kha duyệt D-06/D-07/D-10:** cấm tạo quá khứ; sửa/hủy/xóa chỉ SCHEDULED trước startsAt, chưa từng có booking; giữ hai giá tường minh hiện có. Suất đã có booking kể cả đã hủy khóa toàn bộ thay đổi, không tự chuyển ghế, sửa giá cũ hoặc hủy đơn. Chỉ hủy suất chưa có booking, phải lý do và giữ record CANCELLED; không khôi phục hoặc xóa record đã hủy. Cần Kha quyết định quy trình hủy suất có booking, nới sửa giá sau booking, xóa record canceled và cách chốt capacity_snapshot trước module booking. capacity_snapshot vẫn null, không tự thêm scheduler/chốt sức chứa khác chính sách.

Transaction READ COMMITTED, khóa phim nguồn/đích ID tăng → phòng nguồn/đích ID tăng → suất. Context nguồn đọc bằng projection, sau khóa kiểm tra lại; stale context rollback409, không khóa ngược. Tất cả kiểm tra lịch thực hiện sau khi khóa dòng phòng, giữ khóa đến commit. SELECT lúc này thấy dữ liệu request trước đã commit, không dùng snapshot cũ khi chờ khóa. Cùng giao thức cho tạo/sửa/chuyển phòng/hủy/xóa; DB UNIQUE không được coi là chống mọi khoảng chồng lấn. Module booking sau phải dùng thứ tự phim → phòng → suất → đơn → ghế và xác minh trạng thái sau khóa.

Kiểm thử MySQL8.4.3 cổng3306, DB mới riêng **cinema_management_test_showtimes_20261027_232747**: build/validate và toàn bộ **69/69 đạt** bằng `mvnw -Pmysql-it verify`. 13 test Showtime dùng HTTP, cookie và CSRF thật: điều kiện ACTIVE/ghế, thời gian/offset/giá, mọi dạng trùng và đúng/thiếu15phút, qua nửa đêm, loại chính nó khi sửa, chuyển phòng, snapshot thời lượng, giá/lịch sử booking, hủy/xóa, filter/date/range, HATEOAS gọi được theo vai trò. 56 test cũ tiếp tục đạt.

Bằng chứng đồng thời: [target/showtime-concurrency.json](target/showtime-concurrency.json) và marker SHOWTIME_RACE trong log verify. Mỗi ca dùng hai request HTTP của hai HttpClient/session riêng và hai MySQL CONNECTION_ID khác nhau, được giữ đồng thời bằng barrier **chỉ trong mã kiểm thử** lúc mượn connection. Dùng hai phim khác nhau để test không vô tình chỉ được bảo vệ bởi khóa một phim. Tạo cùng phòng: một201, một409; sửa giờ vào cùng slot: một200, một409; chuyển từ hai phòng vào cùng phòng đích: một200, một409. Truy vấn cuối cho từng phòng đều có số cặp overlap=0; case tạo/chuyển còn kiểm tra chỉ một suất vào phòng đích. Production không có barrier/synchronized hay controller kiểm thử. File evidence là artifact build, không có credential.

DB thử giữ fixture booking/lịch sử từ các lượt chạy (RoomSeat và Showtime), cùng các user INACTIVE không có hash, vì trigger cấm xóa lịch sử. Fixture không lịch sử và tài khoản đăng nhập test được dọn. Không chạy ca lỗi trên dữ liệu thật, không tắt FK/trigger, không sửa SQL/schema hoặc database shop.

Postman hiện **140 request**. Thư mục07 tạo riêng phim/phòng/ghế và lịch, kiểm tra15phút/HATEOAS/filter rồi hủy. **Cố ý giữ suất CANCELLED và phim/phòng/ghế demo liên quan** theo chính sách hiện tại, không reset seed. Credential vẫn để trống; dùng environment riêng. Collection chỉ được kiểm tra JSON/body, chưa chạy trong ứng dụng Postman. **Chưa kiểm chứng tích hợp frontend thật của Đạt hoặc trên máy Đạt.** Chạy lại backend để nạp JAR mới.

16 tệp tạo/điền/sửa (Java dưới `src/main/java/com/khadat/cinema/`):

| Nhóm | Tệp |
|---|---|
| Dependency | `pom.xml` |
| Security | `config/SecurityConfig.java` |
| Module | `controller/ShowtimeController.java`, `service/ShowtimeService.java`, `repository/ShowtimeRepository.java`, `mapper/ShowtimeMapper.java` |
| DTO | `dto/showtime/ShowtimeRequest.java`, `CancelShowtimeRequest.java`, `ShowtimeContext.java`, `ShowtimeView.java`, `ShowtimeResponse.java` |
| Test | `src/test/java/com/khadat/cinema/integration/ShowtimeMySqlIT.java`, `SecurityMySqlIT.java` (bỏ GET lịch giả để dùng endpoint thật) |
| Bàn giao | `README.md`, `docs/api.md`, `docs/postman/cinema.postman_collection.json` |

Chưa commit/push; workspace chưa là Git repository/nhánh. Không sửa frontend hoặc tạo lại project.


## 14. Đặt ghế tại quầy, replay và hủy (28/10/2026)

Đã có GET `/api/showtimes/{id}/seats`, POST/GET `/api/bookings`, GET booking theo `/{id}` hoặc `/by-code/{code}` cho ADMIN/STAFF. POST `/{id}/cancel` chỉ ADMIN + CSRF. Không có DELETE booking, giữ ghế có hạn, khách đặt online, thanh toán/hoàn tiền, email/SMS hoặc frontend mới.

**Cần V001 trước khi chạy JAR mới:** [migration](database/migrations/V001__booking_idempotency.sql) chạy một lần sau baseline. Hai cột nullable bookings.idempotency_key/request_hash, UNIQUE(created_by,key), CHECK cặp metadata và trigger mới bất biến metadata. Không đổi schema.sql/seed.sql cũ, không backfill hoặc xóa lịch sử. Giữ UNIQUE ghế active/trigger cancel đã có. 11 bảng, sau V001 có8trigger. Backend vẫn sql.init=never/ddl-auto=validate, không tự migrate. Nếu báo missing column idempotency_key/request_hash, chọn đúng DB và áp dụng V001 đã kiểm tra, không tắt validate hoặc dùng ddl-auto=update. **Chưa áp dụng migration vào DB sử dụng thực tế của Kha**, chỉ DB test mới.

[API mục9](docs/api.md) ghi request/response, trạng thái, quyền, JSON map và các mã lỗi. Tạo mới yêu cầu `Idempotency-Key` UUID từ frontend, body showtimeId/seatIds/priceVersion/ageChecked. UUID giữ cố định qua retry cùng ý định. Cùng actor/key/canonical body trả booking cũ (201 ban đầu,200 replay, header Idempotency-Replayed); đổi thứ tự ID không đổi hash, ID trùng bị400. Cùng key khác nội dung409. Key theo tài khoản, không có TTL; booking đã hủy vẫn replay đúng record CANCELLED, không đặt lại. Đặt lại sau hủy dùng key mới.

Giá từ DB, STANDARD/VIP dùng hai giá tường minh, BigDecimal cộng VND nguyên, không làm tròn/phụ phí. Client totalAmount/createdBy không được dùng; actor từ session. Tất cả ghế được kiểm tra dưới khóa rồi INSERT phiếu và chi tiết cùng transaction. Snapshot giữ tên/phòng/nhân viên, code/type/giá từng ghế và tổng; thay giá hoặc loại sau này không đổi lịch sử. Model/response không trả hash/key hoặc entity. Map chỉ có vật lý + khả dụng theo suất và giá, không người đặt. Đơn CONFIRMED mới chiếm ghế; cùng ghế ở suất khác độc lập.

Ghi dùng READ COMMITTED, khóa phim→phòng→suất→booking (khi hủy)→ghế theo ID tăng. Context được xác minh lại dưới khóa, đồng bộ với module lịch/phòng/ghế. Map đọc REPEATABLE READ cho một snapshot, không giữ ghế. UNIQUE(showtime_id,active_seat_id) là lớp bảo vệ cuối, không chỉ dựa SELECT hoặc synchronized. BookingTransactionService chứa @Transactional, BookingService facade chỉ giải quyết va chạm idempotency sau khi proxy đã rollback; không đọc lại trong transaction đã lỗi. Không có cơ chế đặt song song khác.

Hủy mới chỉ ADMIN, trước startsAt và suất SCHEDULED. Update đúng booking cha, trigger đổi chi tiết theo booking_id, giữ giá/lịch sử và giải phóng slot trong cùng giao dịch. Hủy lặp200 giữ nguyên audit, kể cả sau giờ chiếu, không giải phóng nhầm ghế booking mới. Chưa cho STAFF hủy, kể cả đơn mình tạo.

**Đề xuất chờ Kha duyệt:** quyền tra cứu toàn rạp/đổi ca và hủy STAFF; thời hạn hủy trước bắt đầu; tối đa20 ghế mỗi đơn; xác nhận kiểm tra tuổi khi snapshot>0; hai giá VIP/thường hiện có. Chưa tự duyệt capacity_snapshot/chốt sức chứa hoặc nới sửa giá suất đã có booking. Module lịch vẫn chặn sửa/hủy/xóa khi có bất kỳ lịch sử booking.

Kiểm thử 28/10/2026: `mvnw -Pmysql-it verify` **84/84 đạt**, MySQL Laragon8.4.3/3306, DB mới `cinema_management_test_bookings_20261028_095639` nhập baseline/seed/V001, Hibernate validate thành công. 15 Booking tests với HTTP, cookie/CSRF thật: map đúng từng suất/phòng, STANDARD+VIP/server price và actor, validation/atomicity, key replay/hash/namespace, hủy/đặt lại/hủy lặp/cutoff, giá lịch sử, lỗi test-only sau INSERT thực sự rollback, UNIQUE DB chống trùng giữa hai phiếu. 69 regression cũ tiếp tục đạt, gồm login/me/logout.

Bảy ca đồng thời có hai HttpClient/session và hai MySQL CONNECTION_ID giữ đồng thời bằng barrier chỉ trong test. [booking-concurrency.json](target/booking-concurrency.json) lưu statuses và kiểm tra duplicateActiveSeats=0/partialBookings=0 cho từng ca:

- Khác request cùng ghế, nhóm ghế giao nhau: một201/một409, chỉ một phiếu đủ ghế.
- Cùng key/cùng nội dung:201/200, chỉ một phiếu; cùng key khác suất:201/409 IDEMPOTENCY_KEY_REUSED, UNIQUE DB buộc transaction thua rollback.
- Hủy và đặt lại: hủy200, đặt201 hoặc409 rồi retry cùng key sau hủy, cuối cùng chỉ một slot hiệu lực.
- Hủy suất và đặt: chỉ một bên ghi được, không booking hiệu lực trên suất đã hủy.
- Sửa giờ suất và đặt: cả hai có thể200/201 theo thứ tự sửa trước→đặt; nếu đặt trước thì sửa409. Response booking và DB có cùng startsAt, không cập nhật lịch sau khi đã có booking.

Test nâng cấp trên DB đã có history trước V001 cũng đạt: `database/test_booking_migration.py`, DB riêng `cinema_management_test_booking_upgrade_20261028_100415`, legacyHistoryPreserved/legacyCancellationWorks=true. [Báo cáo nâng cấp](target/booking-migration-check.json). Runner chỉ tạo DB mới cinema_management_test_*, không DROP/reset. Các booking/history fixture được giữ đúng trigger; tài khoản test có lịch sử chuyển INACTIVE, xóa hash và gỡ role, không còn credential đăng nhập dùng chung.

Postman140request, thư mục08 dựng phim/phòng/ghế/suất riêng, login STAFF đặt/replay/tra cứu, login ADMIN hủy, STAFF đặt lại key mới, kiểm tra hủy lặp không ảnh hưởng booking thay thế. **Giữ toàn bộ lịch sử demo và tài nguyên liên quan**, không reset/delete seed. Credential trống trong file chia sẻ, dùng private environment. Đã kiểm tra JSON/body; chưa chạy trong ứng dụng Postman. **Chưa kiểm chứng tích hợp frontend thật của Đạt hoặc máy Đạt.** Chạy lại JAR sau khi DB đã V001.

25 tệp tạo/điền/sửa (Java dưới `src/main/java/com/khadat/cinema/`):

| Nhóm | Tệp |
|---|---|
| Booking module | `controller/BookingController.java`, `service/BookingService.java`, `service/BookingTransactionService.java`, `repository/BookingRepository.java`, `repository/BookingSeatRepository.java`, `mapper/BookingMapper.java` |
| DTO booking | `dto/booking/BookingRequest.java`, `CancelBookingRequest.java`, `BookingContext.java`, `BookingCreation.java`, `BookedSeatResponse.java`, `BookingResponse.java`, `ShowtimeSeatMap.java` |
| Liên quan cần thiết | `entity/Booking.java` (metadata V001), `repository/SeatRepository.java` (lock ghế ID), `config/SecurityConfig.java` (by-code, ADMIN cancel) |
| Test | `src/test/java/com/khadat/cinema/integration/BookingMySqlIT.java`, `SecurityMySqlIT.java` (bỏ booking giả, dùng validation thật) |
| Database | `database/migrations/V001__booking_idempotency.sql`, `database/test_booking_migration.py` |
| Tài liệu | `README.md`, `docs/api.md`, `docs/database.md`, `docs/database-erd.mmd`, `docs/postman/cinema.postman_collection.json` |

ERD nguồn thêm metadata V001, PNG bước baseline chưa render lại. Không sửa HTML/CSS/JavaScript, pom/cấu hình kết nối, baseline/seed hoặc module lịch/phim/phòng. Chưa commit/push; workspace chưa có Git repository/nhánh.


## 15. Thống kê JDBC và bàn giao cuối đợt 28/10/2026

Đã có4GET chỉ ADMIN: /api/admin/statistics/showtimes, booked-seats, occupancy, cancellations. JdbcTemplate ở repository/jdbc, CRUD JPA giữ nguyên; không thêm dependency/chỉ mục/migration. Định nghĩa trước truy vấn ở docs/statistics.md, hợp đồngAPI mục10. Thiếu snapshot sức chứa quá khứ trảnull, tỷ lệ vượt100 được giữ và đánh anomaly; giá trị booking không gọi doanh thu.

Từ DB trống mới `cinema_management_test_final_20261028_103848` đã nhập schema→seed→V001 theo README và chạy `mvnw -Pmysql-it verify`:88/88 đạt, không skip. JDK25.0.4.1 target21, MySQL8.4.3/3306, Hibernatevalidate. Sau đó JAR đóng gói chạy trên cổng riêng và đạt27bước HTTP từlogin đếnCRUD/book/replay/hủy/phân quyền/stats/logout. Accountdemo ngẫu nhiên đã vô hiệu hóa/xóa hash, đúng tiến trìnhJAR đã dừng, không dừng cổng8080 hoặc xóa DB thật. Dữ liệu lịch sử thử giữ theo trigger.

Postman151request và environment mẫu ở docs/postman/cinema.local.example.postman_environment.json (mật khẩu trống). Import cả hai, chọn privateenvironment, điềnADMIN/STAFFlocal, giữcookiejar, lấyCSRF mới sau login. Nhóm09 đọc statisticsFrom/To. Chưa chạy collection trực tiếp trong ứng dụngPostman; các API đã kiểm tra độc lập bằngJUnit vàJAR.

Frontend hiện26tệp rỗng. **Chưa kiểm chứng qua giao diện của Đạt hoặc máy Đạt**, chưa runtimeJDK21/HTTPS/tải lớn. Không mở rộng frontend hoặc module tài khoản. docs/integration-checklist.md là checklist Đạt nghiệm thu sau; docs/demo.md ghi khởi động/kịch bản; docs/test-cases.md và docs/reports/evidence ghi expected/actual. Báo cáoWord ở docs/reports/Bao_cao_do_an_rap_phim_Kha_Dat.docx; họtên/MSSV/lớp/giảngviên cần nhóm bổ sung.

Mới bổ sung StatisticsController, StatisticsService, StatisticsJdbcRepository, DTOStatisticsResponse/StatisticsRows, các matcherGETADMIN cụ thể và StatisticsMySqlIT. Tài liệu thêm/cập nhật API README statistics test-cases demo integration-checklist, Postman/environment, báo cáo/evidence. Không sửa baseline/migration đã áp dụng hoặc static của Đạt.

Không phát hiện lỗi backend mới sau88test và27bướcJAR. Hạn chế còn lại: thiếu snapshot historical chưa có scheduler; chính sáchD-01…D-12chờKha; UI chưa truycập được bảnhoạtđộng; chưa benchmarkdữliệulớn hoặc thu hồi phiên tức thì khi khóa user. Không đánh dấu toàn bộ đồ án hoàn thành. Chưa commit/push/merge; workspace chưa có Gitrepository/nhánh.

# CineVerse — Đặt vé. Chạm cảm xúc.

Đồ án Java full-stack **bán vé xem phim tại rạp**. Giữ phong cách cinematic đã thiết kế; luồng sản phẩm là chọn phim → rạp/suất → ghế → thanh toán → vé QR. Phim, địa chỉ rạp và lịch chiếu mẫu là hư cấu; vé demo không dùng tại rạp thương mại.

**Đăng ký xong có thể đăng nhập ngay, không cần xác thực email hoặc cấu hình Gmail/Brevo.** Tài khoản đã đăng ký trước đó nhưng chưa xác thực cũng đăng nhập được. Email chào mừng/biên nhận là tác vụ nền; reset mật khẩu và đổi địa chỉ email vẫn sử dụng liên kết email. Các công cụ thiết lập SMTP bên dưới là tùy chọn.

## Bản chạy local

Các địa chỉ đã được truy cập trong quá trình phát triển:

| Thành phần | Địa chỉ |
|---|---|
| Trang người dùng | http://localhost:5173 |
| Trang quản trị riêng | http://localhost:5174 |
| Backend | http://localhost:8080/api/v1 |
| Swagger | http://localhost:8080/swagger-ui/index.html |
| OpenAPI JSON | http://localhost:8080/v3/api-docs |
| Health | http://localhost:8080/actuator/health |
| Mailpit | http://localhost:8025 |
| SQL XAMPP (MariaDB) | 127.0.0.1:3306 / database `cineverse` |

## Công nghệ và cấu trúc

- Java 21, Spring Boot 3.5.16, Spring Security, JWT HS256, BCrypt, JPA/Hibernate, JDBC cho khóa ghế, Validation, Mail, Scheduler, Actuator, Flyway, Springdoc.
- React 19, TypeScript strict, Vite 7, Tailwind 4, các primitive Radix/shadcn, TanStack Query, React Hook Form + Zod, Framer Motion, Lucide, Recharts, Sonner, QRCodeSVG.
- MariaDB 10.4.32 của XAMPP là database local hiện tại; MySQL 8.4 cho Compose. H2 MySQL mode chỉ dùng cho JUnit.

```text
backend/          Java services, REST DTOs, JPA, Flyway, JUnit
frontend/         React khách, UI dùng chung, design tokens, Playwright
admin/            React admin, entry point/build riêng
scripts/          Setup, Maven launcher, start/stop, media
demo-media/       Trailer nguồn mở và WebVTT
docs/             Báo cáo tiếng Việt, ERD, UML, demo, kiểm thử
.runtime/         Database local, secret ngẫu nhiên, log (gitignored)
```

## Khởi động trên Windows / XAMPP

Máy hiện tại đã chuyển dữ liệu sang XAMPP và cấu hình .env. Chạy:

```powershell
pwsh -File scripts/start-local.ps1
pwsh -File scripts/show-demo.ps1
```

Script dùng XAMPP 3306, kiểm thử/build Java và cả hai website, sau đó mở dịch vụ nền ẩn. -SkipBuild chỉ dùng khi bản hiện tại đã build. Cần PowerShell 7, JDK 21, Node 22.12+ hoặc 24 và Mailpit; truyền -MailpitExe nếu đổi đường dẫn. Không cần Apache để chạy React/Java. stop-local.ps1 không dừng XAMPP dùng chung hoặc xóa CSDL.

Cài mới máy khác: tạo .env bằng setup-env.ps1, đặt XAMPP_ROOT, chạy setup-xampp-db.ps1 chỉ với database chưa tồn tại, rồi setup-media.ps1 và start-local.ps1. Xem [XAMPP_SETUP](docs/XAMPP_SETUP.md) về hai ứng dụng, nhập dữ liệu, sao lưu và sự cố phục hồi quyền đã được chủ máy chấp thuận. Không chạy lại migration chuyển dữ liệu trên máy hiện tại.

## Tài khoản demo

- `admin@cineverse.local`: quản trị.
- `free@cineverse.local`: khách hàng đặt vé.
- `vip@cineverse.local`: khách hàng demo thứ hai; tên email được giữ từ nền tảng ban đầu, **không có đặc quyền lấy ghế hoặc xem phim online**.

Mật khẩu được sinh riêng cho máy, đọc bằng `scripts/show-demo.ps1`; không có mật khẩu admin production cố định. Seed chỉ bật với profile `dev`/`test`. Các tài khoản có tên “Khán giả E2E” và đơn mô phỏng có thể xuất hiện sau khi chạy Playwright; đó là dữ liệu kiểm thử đã lưu thật.

## Docker Compose

```powershell
pwsh -File scripts/setup-env.ps1
pwsh -File scripts/setup-media.ps1
docker compose up --build -d
docker compose logs -f backend
docker compose down
```

Compose gồm MySQL 8.4, backend, hai Nginx/React (khách + admin) và Mailpit, có volume bền vững. **Môi trường hiện tại không có Docker CLI/daemon; cấu hình Compose chưa được chạy xác minh.** Không dùng `down -v` nếu cần giữ dữ liệu.

## Các chức năng chính

- Đăng ký và đăng nhập ngay, login/logout, JWT 15 phút, refresh rotation/replay detection, quên/reset mật khẩu, hồ sơ/avatar, đổi email có xác nhận, thu hồi phiên.
- Catalog, tìm kiếm tiếng Việt có/không dấu, lọc/pagination ở backend, chi tiết, trailer, yêu thích, đánh giá và báo cáo.
- Hai rạp demo, bốn phòng, 320 ghế; 140 suất trong 7 ngày khi khởi tạo. Ghế thường/VIP có mức giá khác nhau.
- Giữ 1–8 ghế trong 8 phút; row lock + unique constraint chống bán trùng. Hết hạn/hủy sẽ trả ghế.
- Đơn thanh toán lấy giá từ snapshot ghế trên server. QR chỉ phát hành sau xác nhận; callback trùng không phát hành thêm vé.
- Thanh toán đến sau khi giữ ghế hết hạn được ghi `PAYMENT_REVIEW`, không lấy ghế khách khác.
- Vé của tôi, QR, in vé; admin soát vé dùng một lần.
- Admin CRUD phim, danh mục, rạp, phòng, ghế, suất chiếu; xem đơn/vé/giao dịch; kiểm duyệt, thông báo, email/audit logs, CSV và thống kê từ DB.

## Thanh toán và email

**Phương thức hiện tại là payOS online**, đã viết create link, kiểm chữ ký, webhook, query và phát vé idempotent. .env.example dùng PAYMENT_MODE=payos, PAYOS_ENABLED=false; chưa nhập khóa merchant nên checkout báo chưa kết nối, không tạo giao dịch và không chuyển sang mô phỏng.

**payOS không có Sandbox; thanh toán dùng tiền ngân hàng thật.** Kết nối tại máy bằng `pwsh -File scripts/setup-payos.ps1` sau khi chủ tài khoản xác minh/liên kết ngân hàng và lấy ba khóa riêng. Không gửi khóa vào chat. Xem [PAYOS_SETUP](docs/PAYOS_SETUP.md) và [tài liệu chính thức](https://payos.vn/docs/moi-truong-test/). Chưa thực hiện giao dịch ngân hàng end-to-end.

VNPAY/MoMo Sandbox và simulation được giữ để hồi quy dev/test. Các hướng dẫn [VNPAY](docs/VNPAY_SETUP.md), [MoMo](docs/MOMO_SETUP.md) là phương án cũ, không phải phương thức đang bật. Production chỉ chấp nhận payOS được cấu hình; không bật mock payment trong production.

Email dùng transactional outbox, gửi SMTP sau transaction, retry tối đa 5 lần. Mailpit đã được dùng để nhận email thật qua SMTP trong các test trình duyệt. Gửi SMTP thành công là mail server nhận thư, không đảm bảo người nhận ngoài Internet đã đọc. Xem [EMAIL_SETUP](docs/EMAIL_SETUP.md).

Để tự động gửi đến Gmail thật, chạy `pwsh -STA -File scripts/setup-gmail.ps1`. Nhập tài khoản gửi và mật khẩu ứng dụng Google trong cửa sổ local; công cụ gửi một thư thử đến chính địa chỉ này, lưu .env khi thành công rồi restart backend. Không gửi mật khẩu vào chat. Cần chủ tài khoản cấp mật khẩu ứng dụng; cấu hình local vẫn dùng Mailpit cho đến khi hoàn tất bước này.

## Kiểm thử

```powershell
pwsh -File scripts/maven.ps1 -B test
pwsh -File scripts/maven.ps1 -B package
npm.cmd --prefix frontend ci
npm.cmd --prefix frontend run build
npm.cmd --prefix frontend run lint
npm.cmd --prefix admin run build
cd frontend
npx playwright install chromium
npm run test:e2e
```

JUnit dùng DB H2 riêng và mock SMTP để kiểm tra lỗi/retry. Playwright dùng **backend + MariaDB XAMPP + Mailpit đang chạy**, tạo dữ liệu thật, không mock catalog/auth/payment API. Kết quả và phạm vi xác minh cập nhật ở [TEST_PLAN](docs/TEST_PLAN.md).

## Tài liệu bảo vệ

[Báo cáo](docs/BAO_CAO_DO_AN.md) · [Kiến trúc](docs/ARCHITECTURE.md) · [ERD](docs/DATABASE_ERD.md) · [UML](docs/CLASS_DIAGRAM.md) · [Sequence](docs/SEQUENCE_DIAGRAMS.md) · [API](docs/API_DOCUMENTATION.md) · [Giải thích Java](docs/JAVA_EXPLANATION.md) · [Demo 15 phút](docs/DEMO_SCRIPT.md) · [40 câu hỏi](docs/DEFENSE_QA.md) · [Media/artwork](docs/MEDIA.md)

## Giới hạn cần biết

- Không phải hệ thống vé kết nối rạp thương mại; không triển khai production/hardening hoặc load test.
- Chưa có hoàn tiền tự động, API đối tác rạp, combo bắp nước, mã khuyến mãi, hóa đơn thuế hoặc ứng dụng staff quét camera. Máy quét QR dạng bàn phím/nhập mã được hỗ trợ.
- Lịch demo cố định trong 7 ngày từ lúc seed; admin tạo thêm suất khi cần. Script không tự xóa hoặc dời suất đã có đơn.
- Dữ liệu lịch sử streaming/subscription ban đầu còn trong schema để bảo toàn dữ liệu; giao diện và luồng thanh toán hiện tại phục vụ bán vé. API phát phim đầy đủ đã được gỡ; trailer vẫn hoạt động.
- Giới hạn một instance: rate limit và vé truy cập trailer ở bộ nhớ; outbox có cơ chế claim/retry nhưng SMTP có thể gửi trùng nếu crash ngay sau SMTP accept và trước khi đánh dấu SENT.
- Catalog/search dùng LIKE và phân trang offset, phù hợp đồ án. Danh sách vé cá nhân giới hạn 100 đơn gần nhất; CSV admin giới hạn 10.000 dòng.

## Xử lý sự cố

- `401` khi vào tài khoản: đăng nhập lại; `/auth/refresh` yêu cầu `Origin` khớp `FRONTEND_URL` hoặc `ADMIN_URL` tương ứng. Dùng thống nhất `http://localhost:5173`, tránh trộn `127.0.0.1` trong trình duyệt.
- Không có lịch: chọn ngày trong 7 ngày demo, hoặc thêm suất bằng admin. Backend đóng bán trước giờ chiếu 10 phút.
- Ghế vừa bị giữ: tải lại sơ đồ và chọn ghế khác; server là nơi quyết định cuối cùng.
- Email PENDING/FAILED: kiểm tra Mailpit/SMTP, `last_error`, lịch retry và nút gửi lại trong admin.
- JAR không ghi đè được trên Windows: dừng backend của project trước khi `package` rồi start lại.
- Trailer lỗi: chạy setup-media, kiểm tra `MEDIA_ROOT` tương đối với thư mục backend và quyền đọc tệp.

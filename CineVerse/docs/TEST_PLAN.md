# Kế hoạch và bằng chứng kiểm thử

## Bộ 40 poster điện ảnh (05/11/2026)

Đã tạo 39 PNG bằng imagegen, giữ poster mẫu đầu tiên. Kiểm file cả bộ: 40 ảnh khác SHA-256, đúng kích thước 1024×1536, tổng dung lượng 109.886.164 bytes. Danh sách chính xác tại frontend/public/art/poster-collection.json; prompt 39 ảnh trong docs/prompts/poster-collection-v1.json. Ảnh gốc SVG vẫn giữ để khôi phục; seed/generator dùng registry mới.

Đã cập nhật 39 poster qua API admin, xác minh đủ 40 phim và hai phim “test”/“Phim kiểm thử E2E” không đổi metadata. Biên bản: .runtime/poster-collection-applied.json; backup trước cập nhật: .runtime/backups/posters-20261105-225007.json. Lượt đối chiếu đầu dừng tại phim 1 chưa cần cập nhật, do API trả cùng tập genreIds theo thứ tự khác nhau; đã xác minh bằng hai lần đọc, sửa phép so sánh để sắp xếp genreIds, rồi chạy lại thành công. Không bỏ kiểm tra các trường khác và không sửa nghiệp vụ Java chỉ để vượt kiểm tra.

Customer build đạt (.runtime/logs/poster-collection-customer-build.log), admin build đạt (.runtime/logs/poster-collection-admin-build.log). Chromium và HTTP kiểm trên web đang chạy: đủ 40 phim gốc trả URL mới, 40 URL ảnh HTTP 200/đúng kích thước, hai ảnh loại trừ vẫn fallback.svg; khám phá desktop/mobile 375 không tràn ngang, home, 5 trang chi tiết thuộc thể loại khác nhau, admin có 12 poster mới trên trang đầu, gallery tải đủ 40 ảnh, 0 lỗi JavaScript. Kết quả: .runtime/poster-collection-browser-check.json. Đã xem trực tiếp ảnh tổng hợp 40 poster và ảnh mobile.

Screenshots tại docs/screenshots/poster-collection-*: gallery.png, discover-1440.png, discover-375.png, home-1440.png, detail-1440.png, admin-1440.png. Không có thay đổi nghiệp vụ Java nên không chạy lại JUnit cho lượt thay ảnh này. Giao dịch ngân hàng không nằm trong phạm vi thay poster.

## Mẫu poster chân thật (05/11/2026)

Thay đúng một poster của `phia-ben-kia-chan-troi` bằng PNG imagegen 1024×1536; nguồn/prompt tại ASSETS.md. Cập nhật dữ liệu đang chạy qua API admin, kiểm lại public API và đăng xuất phiên công cụ. Hai build customer/admin đạt, logs `.runtime/logs/poster-customer-build.log` và `poster-admin-build.log`; JSON seed vẫn 40 phim, đúng một poster mới, generator qua `node --check`. Không đổi nghiệp vụ Java nên không chạy lại bộ JUnit cho thay đổi ảnh.

Chromium xác minh URL ảnh và natural dimensions, ảnh tải đúng tại trang chi tiết desktop 1440, mobile 375 và thẻ trang chủ, không tràn ngang mobile hoặc lỗi JavaScript. Screenshots `poster-preview-detail-1440.png`, `poster-preview-detail-375.png`, `poster-preview-card.png` trong docs/screenshots; đã xem trực tiếp desktop và thẻ phim. Ảnh SVG cũ vẫn được giữ để hoàn tác.

## Hiện hành: hai trang riêng, XAMPP và payOS (05/11/2026)

Backend Maven package đạt **80/80, 0 failure/error/skip**, BUILD SUCCESS tại .runtime/logs/final-java-tests.log. Thêm 5 test PayosPaymentGateway (create amount/code/host, query binding, chữ ký SDK, disabled fail-closed), 2 integration test thiếu/đủ tiền/idempotency/sai amount/late payment và 1 test cookie khách/admin độc lập. SDK transport/create/query trong test dùng mock, chữ ký webhook dùng SDK thật với khóa giả chỉ dành cho test; không gọi API payOS hoặc chuyển tiền.

Lượt package đầu sau test bị khóa JAR do backend đang chạy; đã dừng đúng tiến trình của dự án và sửa start-local để dừng backend trước build. Lượt sau phát hiện ConcurrentModificationException khi Spring Security ghi header cùng luồng phát trailer async; đã chuyển ghi security header sang request thread trước streaming rồi chạy lại toàn bộ đạt 80/80. Không bỏ qua test lỗi.

Customer và admin đều build thành công, TypeScript/lint đạt; output frontend/dist và admin/dist riêng. Playwright chạy trên Java + MariaDB XAMPP 3306 + Mailpit thật: lượt đầy đủ có 11 đạt/2 lỗi assertion/3 bỏ qua (.runtime/logs/xampp-two-apps-e2e.log). Hai assertion sai kỳ vọng URL logout có next và tên trường errorCode; đã sửa đúng hợp đồng và chạy lại cả bốn test separate-apps, **4/4 đạt** (.runtime/logs/xampp-separate-apps-fixed.log). Các kiểm tra còn lại đạt trong lượt đầy đủ; không gộp thành tuyên bố một lượt toàn bộ đều xanh. Ba test bỏ qua là hai fixture trả tiền simulation và readiness VNPAY không áp dụng khi mode=payos.

Bốn test riêng xác nhận: cùng browser giữ cv_refresh/cv_admin_refresh, reload đúng user, logout admin vẫn giữ khách; USER không vào admin; sửa phim ở admin hiển thị trên trang khách đang mở sau 30 giây rồi phục hồi tên ban đầu; thiếu khóa payOS trả 503 PAYOS_NOT_CONFIGURED. Browser cũ cũng đã đạt race ghế, CRUD rạp/phòng/suất/overlap, đăng ký không VERIFY, reset SMTP, CRUD phim, tất cả menu admin và responsive sáu viewport. Screenshots mới admin-login-375/768/1440 và admin-movies-1440 đã xem trực tiếp.

XAMPP: đối chiếu đủ 31 bảng nguồn/đích trước Flyway V5; CHECK TABLE mysql.db và mysql.tables_priv OK sau phục hồi mặc định có sự cho phép của chủ máy. V5 được backend áp dụng thành công, hiện 32 bảng gồm flyway_schema_history. Xem XAMPP_SETUP.md cho backup và giới hạn phục hồi quyền cũ.

Chưa có merchant keys nên PAYOS_ENABLED=false, chưa kiểm thử end-to-end payOS ngân hàng, webhook Internet hoặc Docker. payOS không có Sandbox; các kết quả unit/integration không phải bằng chứng giao dịch ngân hàng thật.

Các mục dưới đây là lịch sử trước thay đổi hiện hành.


## Tích hợp VNPAY Sandbox (thay lựa chọn MoMo)

Backend mới đạt **72/72**: thêm 5 unit test signature/url/query và 4 integration test IPN/idempotency, cancel/late payment, return không mark paid, duplicate params và quyền owner đối soát. Query dùng mock transport, IPN dùng chữ ký kiểm thử riêng; không dùng credential merchant của người khác hoặc gọi đó là bằng chứng giao dịch Sandbox đã thực hiện. Giá tối thiểu 5.000đ, amount ×100, thời gian Asia/Ho_Chi_Minh và HMAC SHA512 được kiểm tra. Frontend lint/build đạt. Script ghi cấu hình đã kiểm trên file tạm, giữ key khác và từ chối secret có xuống dòng.

Log: .runtime/logs/vnpay-build.log. **11/11 browser hồi quy đạt** trên cấu hình simulation cũ trước khi đổi default (.runtime/logs/vnpay-regression-e2e.log). Sau khi đổi VNPAY: **10 đạt, 2 bỏ qua có lý do** (.runtime/logs/vnpay-active-e2e.log); hai fixture tạo vé bằng simulation không được chạy khi mode=vnpay và đã đạt ở lượt trước. Test readiness VNPAY xác minh UI/503 không tạo order, cấm simulate và không tràn ngang ở 375/1440; đã xem screenshot thực tế. Admin đã bổ sung tổng/chart VNPAY+MoMo Sandbox, hiển thị rõ tiền thử nghiệm và nhãn provider đúng; Java test kiểm tổng VNPAY sau IPN hợp lệ.

Chưa có TmnCode/HashSecret riêng nên chưa làm giao dịch trên trang VNPAY; đăng ký/thẻ Test xem VNPAY_SETUP.md. Return/IPN/query đã kiểm bằng chữ ký và transport kiểm thử, không được coi là chứng minh gateway đã nhận merchant thật. Các phần MoMo/simulation phía dưới là lịch sử.

## MoMo Sandbox — lần xác minh mới

Build backend đạt **63/63** (53 kiểm thử trước + 9 HTTP adapter + 1 readiness/fail-fast). Mock HttpClient thay transport để kiểm định hợp đồng gửi nhận, không thay đổi endpoint Sandbox cố định. Trường hợp: create captureWallet/HMAC/giá server, response sai order/request, URL Production/ngoại miền/port lạ, thiếu IPN, merchant rejection, timeout, query signature và binding. Kiểm fail-fast xác nhận thiếu cấu hình không đụng booking/repository. Frontend build/lint đạt. Script cấu hình được kiểm với file tạm để giữ cấu hình khác, loại key trùng và từ chối callback HTTP/localhost/sai path.

Log backend: .runtime/logs/momo-sandbox-build.log. Browser regression đạt **11/11**, log .runtime/logs/momo-sandbox-e2e.log. Website vẫn chạy simulation vì chưa có merchant keys/IPN public; chưa thực hiện giao dịch qua MoMo thật, chưa mua dịch vụ hoặc mở tunnel public.

## Thay đổi hiện hành: bỏ xác thực email đăng ký

Login/refresh/SessionGuard không yêu cầu verified; user.locked và sessionVersion vẫn được kiểm tra. Signup không tạo VERIFY token/outbox; chào mừng và notification được tạo ngay. Tài khoản cũ verified=false đăng nhập được; không giả định verified=true. V4 ngừng gửi VERIFY còn chờ. API resend cũ trả thông báo đăng nhập ngay, không gửi thư. Reset/đổi email vẫn dùng token.

JUnit được cập nhật để kiểm đăng nhập với mật khẩu đúng, từ chối mật khẩu sai, refresh và API protected cho user chưa xác thực, reset cho user mới, từ chối tài khoản khóa. Playwright bước đầu chuyển thành đăng ký rồi đăng nhập ngay không chờ email. Bản mới đạt **53/53 Java, 11/11 Playwright**, frontend lint/build đạt; Flyway V4 đã áp dụng trên MySQL local. Log: .runtime/logs/no-email-verification-build.log và .runtime/logs/no-email-verification-e2e.log. Các mô tả xác thực đăng ký bên dưới là bằng chứng lịch sử trước thay đổi này.

Môi trường: Windows, Java 21, Node 24, MySQL 8.4 riêng cổng 3307, Spring Boot 8080, Vite 5173, Mailpit SMTP 1025/UI 8025. Cập nhật 05/11/2026.

## Backend
Lệnh: pwsh -File scripts/maven.ps1 -B package.
Kết quả bản cuối: **53 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS** (51 integration và 2 unit test rate limit).
Báo cáo gốc: backend/target/surefire-reports/com.cineverse.CineVerseIntegrationTest.txt và com.cineverse.RateLimitFilterTest.txt; log local: .runtime/logs/final-junit.log.

JUnit dùng H2 MySQL mode, Spring context, MockMvc, mock SMTP; không gọi merchant MoMo thật. Bao gồm auth/rotation/token một lần, quyền, catalog/community, gateway signature/binding, payment idempotency, outbox retry và tương thích dữ liệu legacy.

Các trường hợp booking: server price, seat trùng/sai phòng, owner 403, hủy/hết hạn trả ghế, phụ thu VIP, tạo order lặp, phát ticket/receipt một lần, callback đến muộn không chiếm ghế người khác, check-in một lần, trùng giờ. Test mới kiểm cả failure và cancel trả ghế, callback thành công đến sau hủy chuyển PAYMENT_REVIEW.

## Frontend
npm.cmd run lint: đạt, không có lỗi/cảnh báo ESLint tại lần xác minh.
npm.cmd run build: TypeScript và Vite đạt. Rollup thông báo loại annotation của dependency Zod; không phải lỗi build.

## Browser trên dịch vụ thật
Chạy npm.cmd run test:e2e trong frontend khi các dịch vụ local đang hoạt động. Playwright Chromium một worker, không mock auth/catalog/payment API. Provider simulation vẫn là mô phỏng, không phải MoMo.

11 test bao phủ:
1. Hai user tranh cùng ghế MySQL: đúng một 201, một 409; owner 403; admin không đóng suất/phòng đang có hold; hủy trả ghế.
2. Admin tạo rạp/phòng 80 ghế/suất; từ chối overlap rồi đóng dữ liệu test.
3. Staff check-in một lần; seatmap sáu viewport không tràn ngang.
4. Đăng ký → nhận SMTP → xác thực → login.
5. Tìm kiếm backend → chọn suất → giữ ghế.
6. Yêu thích, cập nhật đánh giá, thông báo bền vững.
7. Checkout → simulation → QR → SMTP → fulfillment lặp an toàn.
8. Reset qua SMTP rồi đăng nhập mật khẩu mới.
9. Admin tạo, công bố, sửa, ẩn phim DB.
10. Các menu admin hiện tại render từ API, không có runtime exception.
11. Home sáu viewport không overflow/runtime exception.

Kết quả lượt cuối: **11/11 passed**, runner exit code 0. Log: .runtime/logs/final-e2e.log; HTML: frontend/playwright-report/index.html. Dữ liệu E2E được lưu vào DB local, không xóa lịch sử giao dịch để làm đẹp kết quả.

## Kiểm tra hình ảnh
Screenshot tại docs/screenshots: home và seats ở 375, 390, 768, 1366, 1440, 1920 pixel; ticket mobile/desktop và chi tiết mobile. Đã xem ảnh hero, seatmap và vé. Ảnh không thay thế assertions nghiệp vụ.

## Giới hạn bằng chứng

### Thiết lập Gmail (bổ sung)

Sau bổ sung STARTTLS required và kiểm tên máy chủ TLS: Maven package đạt 53/53, log .runtime/logs/gmail-build.log; health local UP. Công cụ setup-gmail.ps1 đã qua kiểm tra dựng WinForms không hiển thị và kiểm tra ghi .env trên tệp tạm: thay thế key trùng, giữ cấu hình DB, bỏ khoảng trắng mật khẩu ứng dụng, bật TLS và từ chối dữ liệu không hợp lệ. Chưa dùng credential thật hoặc gửi SMTP Internet trong các kiểm tra này. Gmail chỉ được xem là cấu hình sau khi chủ tài khoản nhập credential và bấm gửi thử thành công trong cửa sổ local.
Lượt E2E đầu sau gián đoạn bị ECONNREFUSED vì dịch vụ đã dừng; không tính là đạt. Lượt tiếp theo đạt 10/11, trace xác định refresh HTTP 429 do dùng cùng mức 20/phút như đăng nhập. Đã tách refresh 120/phút, giữ đăng nhập 20/phút và thêm hai unit test kiểm cả giới hạn và sự độc lập giữa endpoint; sau đó chạy lại toàn bộ.

Chưa chạy MoMo Sandbox end-to-end, SMTP Internet, Docker Compose, load test, kiểm chứng nhiều instance hoặc production. Test H2 không thay thế mọi hành vi khóa MySQL; race test hai user trên MySQL chỉ kiểm trường hợp cạnh tranh cụ thể. Không có đo SLA/throughput.

# Tiến độ CineVerse — bán vé xem phim

Yêu cầu mới nhất: hai trang khách/admin riêng dùng CSDL XAMPP, thanh toán online payOS. Đã tách entry point/build/router và cookie refresh ở 5173/5174, chuyển dữ liệu sang MariaDB XAMPP 3306 và đối chiếu đủ 31 bảng nguồn. Theo sự cho phép của chủ máy, khôi phục hai bảng quyền XAMPP bị hỏng từ bản mặc định sau khi sao lưu. V5 thêm payos_links (hiện 32 bảng gồm lịch sử Flyway). Xem docs/XAMPP_SETUP.md.

payOS đã có create/query/webhook ký và kiểm binding, chống phát vé trùng/thiếu tiền, thống kê online riêng. Chưa có merchant keys nên PAYOS_ENABLED=false, checkout không tạo giao dịch và không tự chuyển mô phỏng. payOS không có Sandbox, dùng tiền ngân hàng thật; script nhập khóa che ký tự và hướng dẫn ở docs/PAYOS_SETUP.md. Chưa chuyển tiền hoặc gọi API tạo link thật. Các adapter Sandbox cũ chỉ để hồi quy dev/test.

Cập nhật 05/11/2026. Phạm vi streaming/subscription đã thay bằng bán vé theo yêu cầu, giữ Java/React và thiết kế cinematic.

- [x] Thử đúng một poster chân thật hơn cho “Phía bên kia chân trời” bằng imagegen; lưu PNG trong public/art, giữ ảnh cũ, ghi nguồn/prompt và cập nhật seed/generator. Các phim khác giữ nguyên ảnh.
- [x] Phủ poster điện ảnh cho đủ 40 phim gốc: 39/39 ảnh mới bằng imagegen và giữ mẫu đã duyệt. Đã gán qua API admin, sao lưu metadata, xác minh hai phim “test” và “Phim kiểm thử E2E” giữ nguyên toàn bộ form. 40 PNG khác nhau, 1024×1536; kiểm 40 URL HTTP thành công, desktop/mobile/admin và 5 trang chi tiết không có lỗi JavaScript; hai build đạt. Registry, prompt, gallery và bằng chứng tại docs/ASSETS.md, docs/TEST_PLAN.md và .runtime/poster-collection-*.json.

Bổ sung launcher `Cai-dat-payOS.cmd` cho Windows không có pwsh trong PATH; tự chuyển đúng thư mục và tìm PowerShell 7/runtime có sẵn. Backend restart dùng chính PSHOME, tránh lỗi tìm pwsh lần nữa sau khi nhập khóa.

Yêu cầu mới: bỏ xác thực email khi đăng ký. Đã bỏ gate verified ở login/refresh/SessionGuard, không tạo VERIFY khi đăng ký; tài khoản cũ chưa xác thực vẫn dùng được. V4 hủy thư VERIFY đang chờ. UI đăng ký/hồ sơ và kiểm thử đã đổi theo luồng đăng nhập ngay. SMTP/Gmail/Brevo trở thành tùy chọn, không còn là điều kiện hoàn tất đăng ký. Kết quả xác minh mới xem docs/TEST_PLAN.md.

- [x] Java/Spring Boot, React TypeScript, MySQL và Flyway.
- [x] Auth đăng ký không xác thực email, refresh rotation, reset, hồ sơ và quyền owner/admin.
- [x] Catalog, tìm kiếm/lọc/phân trang, yêu thích, đánh giá, thông báo.
- [x] Rạp, phòng, ghế, suất chiếu và chống trùng giờ.
- [x] Giữ ghế nguyên tử, snapshot giá, hết hạn/hủy trả ghế.
- [x] Checkout booking, simulation có nhãn, vé QR, check-in một lần.
- [x] MoMo adapter, HMAC/IPN/idempotency/query với kiểm thử tự động.
- [x] Củng cố MoMo Sandbox: binding response create, chặn payUrl ngoài Sandbox, readiness không lộ khóa, lỗi tạo bị từ chối trả ghế, nhập cấu hình bằng script che khóa và hướng dẫn lấy merchant Test. 63 kiểm thử Java đạt; không bật MoMo khi chưa có khóa.
- [x] Outbox SMTP, retry, Mailpit, biên nhận vé.
- [x] Admin CRUD, thống kê DB, CSV, email/audit logs.
- [x] Gỡ API phát phim đầy đủ; trailer có attribution.
- [x] Build backend/frontend và lint đạt; 72 kiểm thử Java đạt. Hồi quy browser 11/11 trước chuyển provider; bản VNPAY 10 đạt, 2 fixture simulation bỏ qua có lý do. Chưa có khóa riêng để chạy giao dịch VNPAY thực tế.
- [x] README, báo cáo 26 phần, UML/ERD/API, Java, 45 câu hỏi, demo.
- [x] Dockerfiles và Compose được soạn.
- [x] Hai website khách/admin riêng; USER bị từ chối admin, đăng xuất admin không làm mất phiên khách, admin sửa phim được trang khách tự đọc lại từ cùng CSDL.
- [x] XAMPP MariaDB hoạt động; sao lưu, đối chiếu dữ liệu nguồn/đích, Flyway V5 và tài khoản DB riêng chỉ trên cineverse.*.
- [x] payOS SDK + create/query/webhook, readiness, thanh toán thiếu/đủ/đến muộn và idempotency; 80/80 Java đạt, build/lint cả hai website đạt. Bốn kiểm tra browser riêng cho tách trang/payOS đạt; lịch sử các lượt khác ở TEST_PLAN.md.
- [ ] payOS ngân hàng end-to-end: cần chủ tài khoản xác minh/liên kết ngân hàng và nhập Client ID/API Key/Checksum Key trên máy; chưa bật thanh toán thật.
- [ ] MoMo Sandbox end-to-end: cần merchant credential và IPN HTTPS.
- [x] Công cụ thiết lập Gmail có nhập mật khẩu che ký tự, thư thử gửi cho chính mình, lưu .env và restart backend; cấu hình STARTTLS bắt buộc.
- [ ] Tùy chọn ngoài phạm vi đăng ký hiện tại: xác minh SMTP Gmail/Brevo Internet bằng credential thật.
- [x] Thêm thiết lập Brevo trong trình duyệt local để thay cửa sổ Windows; đã kiểm trang HTTP, từ chối POST sai Origin và ghi cấu hình trên tệp tạm. Chưa có credential/quyền gửi Brevo để kiểm SMTP Internet.
- [ ] Chạy Compose: máy chưa có Docker CLI/daemon.
- [ ] Production/load test/backup nhiều instance: ngoài phạm vi demo local.

Bằng chứng cuối xem docs/TEST_PLAN.md. Media/license xem docs/MEDIA.md; artwork xem docs/ASSETS.md. Không có hoàn tiền tự động; lịch seed có hạn; không kết nối rạp thương mại.

# Use cases

Actors: khách chưa đăng nhập, khách hàng đã xác thực, ADMIN/nhân viên soát vé, MoMo Sandbox, SMTP worker và scheduler. Staff hiện dùng role ADMIN, chưa tách role riêng.

| Use case | Điều kiện | Luồng chính | Nhánh khác | Kết quả |
|---|---|---|---|---|
| Đăng ký | email chưa tồn tại | validate → hash password → user + welcome notification | email trùng/đầu vào sai | đăng nhập được ngay, không cần email |
| Đăng nhập | không locked, không yêu cầu verified | BCrypt → JWT + refresh cookie | sai mật khẩu, khóa, replay refresh | phiên hợp lệ |
| Tìm phim | public | nhập từ khóa/bộ lọc URL → backend pagination → detail | không kết quả/API lỗi | danh sách phù hợp |
| Chọn suất | phim published, rạp/phòng active | ngày → rạp → suất tương lai | chưa có lịch/đóng bán trước 10 phút | thông tin suất |
| Giữ ghế | login, 1–8 ghế cùng phòng | lock suất → dọn expired → tính giá → reservations | ghế trùng, sai phòng, quá 3 hold | HELD 8 phút |
| Hủy giữ ghế | chủ đơn, chưa confirmed | kiểm tra quyền → lock → xóa held reservations | đơn đã paid/confirmed | CANCELLED, ghế trả về |
| Thanh toán | hold còn hạn | order giá server → gateway → callback xác thực | fail/cancel/pending/late | PAID và CONFIRMED hoặc REVIEW |
| Nhận vé | payment hợp lệ, reservations còn | ticket mỗi ghế → notification + outbox | callback trùng no-op | vé QR, mail có liên kết |
| Soát vé | admin, mã ISSUED | khóa ticket → USED + audit | mã sai/đã dùng/suất quá cũ | chấp nhận một lần |
| Quản lý suất | admin, phim/phòng hợp lệ | nhập giờ/giá → lock room → kiểm overlap → save | trùng giờ hoặc đã có đơn | suất mở bán, không phá đơn cũ |
| Hồ sơ/bảo mật | login | update/avatar/change password/email | password sai, email cần xác nhận | dữ liệu cập nhật/thu hồi phiên |
| Kiểm duyệt | admin | xem report → ẩn/hiện có reason → audit | không đủ quyền | nội dung public được kiểm soát |
| Email retry | FAILED | admin đưa lại PENDING → worker | SMTP tiếp tục lỗi | attempt log, không rollback vé |

Use case thanh toán mô phỏng được trình bày rõ là development simulation. Không có use case xem toàn bộ phim online trong sản phẩm hiện tại; trailer chỉ phục vụ giới thiệu phim.

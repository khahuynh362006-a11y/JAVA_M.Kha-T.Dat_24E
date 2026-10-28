# Checklist tích hợp cho Đạt

Trạng thái 28/10/2026: **Chưa kiểm chứng qua giao diện của Đạt**. 26 tệp trong static/ hiện rỗng; không có form/JavaScript hoạt động để chạy luồng UI. Không sửa hoặc viết lại frontend ở bước này. Backend88test và JAR27bước HTTP đạt, không thay thế nghiệm thu UI hoặc máy Đạt.

## Chuẩn bị

- [ ] Import database đúng baseline→seed→V001 trên DB riêng; không chạy lại baseline vào DB có dữ liệu.
- [ ] Đặt JAVA_HOME/DB_* trên máy Đạt, build/chạy Wrapper theo README; thử health200 và Hibernate validate.
- [ ] Tạo ADMIN/STAFF riêng bằng local-accounts; không dùng credential minh chứng đã bị vô hiệu hóa.
- [ ] Import collection và environment mẫu, chọn environment riêng; điền mật khẩu bằng giá trị local/secret, không export lại cho nhóm.
- [ ] Chốt D-02/D-12 và các đề xuất còn lại với Kha; STAFF hiện không được hủy.

## Hợp đồng cần đối chiếu từng màn hình

- [ ] Cookie HttpOnly/SameSite=Lax, fetch cùng origin có credentials; GET csrf→login JSON→GET csrf mới; logout POST có token.
- [ ] Status/code/fieldErrors hiển thị đúng; không biến401/403/503 thành danh sách rỗng; không log token/password.
- [ ] page bắt đầu0, size tối đa100; kiểm tra trang rỗng/trang vượt tổng và nullable.
- [ ] Phim/genre: PUT gửi đủ trường, trường tùy chọn bị bỏ qua sẽ null; map genreIds; URL placeholder có ảnh thay thế.
- [ ] Phòng/ghế: dùng route nested; seatCount là số vật lý, không phải ghế trống; A2 trướcA10, INACTIVE không chọn; bulk không reset.
- [ ] Lịch: ISO +07:00, thời gian phút; không gửi endsAt/status; hiển thị kết thúc qua ngày. date lọc ngày bắt đầu.
- [ ] HATEOAS detail ở root+_links, chỉ hiện hành động được phép; STAFF không theo link quản trị.
- [ ] Sơ đồ suất: phân biệt physicalStatus/availability và bookable; đọc map không giữ ghế.
- [ ] Đặt ghế: priceVersion từ map, ageChecked, 1–20 ID không trùng; server quyết định giá/người tạo.
- [ ] Sinh UUID cho mỗi ý định; retry timeout cùng UUID/body; thay lựa chọn/giá/tuổi dùng key mới; replay booking đã hủy không đặt lại.
- [ ] Sau409 ghế/giá: tải map mới, giữ lựa chọn còn trống rồi xin xác nhận; không tự báo thất bại chắc chắn khi mất response.
- [ ] ADMIN hủy giữ audit/giá; hủy lặp không ảnh hưởng đơn đặt lại; STAFF403 với token hợp lệ.
- [ ] Thống kê: phân biệt phiếu/ghế/giá trị, ngày chiếu/ngày hủy; null tỷ lệ là thiếu mẫu số/0, không hiển thị0%; tỷ lệ>100 đánh dấu anomaly, không cắt100.

## Luồng UI cần Đạt chạy và lưu bằng chứng

1. ADMIN login→genre→phim→phòng→bulkghế→suất→HATEOAS/chi tiết.
2. STAFF login→lịch→map→đặt2ghế→tra cứuID/mã→retry cùng key→mã/ID không đổi.
3. ADMIN hủy→STAFF đặt lại key mới→ADMIN hủy lặp đơn cũ→đơn mới vẫn CONFIRMED.
4. Thử trùng lịch/ghế, sai phòng, giá đổi, thiếu CSRF, sai quyền, phim/phòng ngừng và xóa có liên kết.
5. ADMIN xem4bảng JDBC, thử ngày rỗng/sai/khoảng32ngày, occupancy thiếu snapshot và mẫu số0.

Với mỗi lỗi: ghi trang, tài khoản vai trò, bước tái hiện, URL/method, status/code và response đã che bí mật, expected/actual. Không chụp headerCookie/CSRF/Idempotency nội bộ hoặc mật khẩu. Kha chỉ sửa backend nếu lỗi thuộc backend; thay hợp đồng cần đồng bộ docs trước. Chưa có bằng chứng máy Đạt nên không đánh dấu hoàn tất phần đó.

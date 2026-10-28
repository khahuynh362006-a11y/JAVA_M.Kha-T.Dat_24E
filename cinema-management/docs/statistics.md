# Định nghĩa thống kê quản trị

Ngày 28/10/2026. Bản triển khai dùng định nghĩa sau cho SC-11/BR-024; các lựa chọn D-12 và việc chốt capacity_snapshot vẫn chờ Kha duyệt. Không có doanh thu thực thu vì hệ thống chưa thu tiền.

| API | Đơn vị và công thức | Nguồn và trạng thái | Thời gian lọc |
|---|---|---|---|
| showtimes | Mỗi ngày: tổng suất, SCHEDULED, CANCELLED | showtimes, tính cả hai trạng thái; SCHEDULED bao gồm suất đã kết thúc theo giờ | starts_at |
| booked-seats | Theo phim: số phiếu CONFIRMED, số ghế CONFIRMED, tổng giá trị phiếu | Chỉ suất SCHEDULED; đếm phiếu và ghế ở hai aggregate riêng, không JOIN thể loại | starts_at của suất |
| occupancy | Ghế CONFIRMED / mẫu số hợp lệ ×100 | Chỉ suất SCHEDULED; không tính ghế đã hủy hoặc suất CANCELLED | starts_at |
| cancellations | Mỗi ngày: số phiếu hủy, số ghế của phiếu hủy, giá trị danh nghĩa của phiếu hủy | bookings CANCELLED; tính sự kiện hủy kể cả suất sau đó hủy; giá trị không phải hoàn tiền | cancelled_at, không dùng created_at |

Giờ Việt Nam Asia/Ho_Chi_Minh/+07:00. from/to là ngày, bao gồm cả hai đầu, SQL dùng [from 00:00,to+1 00:00), tối đa31ngày. Ngày không dữ liệu trả0 trong hai bảng theo ngày; bảng phim/suất không có dữ liệu trả items=[]/total=0. page từ0, size1–100/default20; thứ tự ngày tăng, movieId tăng hoặc starts_at/id tăng. Optional movieId/roomId là bind parameter, không có SQL tùy ý hoặc cột sort từ request.

Ngày chiếu là ngày bắt đầu suất; suất 23:00–01:00 thuộc ngày bắt đầu. Ngày tạo booking chỉ là audit, không được dùng thay ngày chiếu hoặc ngày hủy. Hủy đúng00:00 thuộc ngày mới. Giá trị CONFIRMED cộng total_amount mỗi phiếu một lần; số ghế đếm chi tiết riêng. Lịch sử cùng ghế đã hủy rồi đặt lại chỉ đếm lần CONFIRMED trong chỉ số đang đặt.

## Mẫu số lấp đầy

1. capacity_snapshot có giá trị: dùng SNAPSHOT.
2. Snapshot null và chưa tới starts_at: dùng số ghế vật lý ACTIVE hiện tại, capacityBasis=CURRENT_ACTIVE_SEATS_PROVISIONAL. Đây là tạm tính, có thể thay đổi; không phải số ghế trống hay trạng thái phòng mở bán.
3. Snapshot null và đã tới starts_at: capacity=null, occupancyPercent=null, capacityBasis=MISSING_HISTORICAL_CAPACITY. Không lấy sơ đồ hiện tại để bịa lịch sử.

Capacity=0 trả occupancyPercent=null; không chia0. Tỷ lệ khác null làm tròn2 chữ số HALF_UP, không chặn ở100; bookedSeats>capacity đánh anomaly=true. Không ghi hoặc backfill snapshot ở API đọc. Scheduler/chính sách chốt sức chứa trước hoặc lúc bắt đầu vẫn chờ Kha duyệt; hiện phần lớn suất lịch sử chưa có snapshot nên chưa thể kết luận tỷ lệ lịch sử.

## Phân quyền và triển khai

GET /api/admin/statistics/{showtimes|booked-seats|occupancy|cancellations}, chỉ ADMIN. Cookie phiên hiện có, GET không cần CSRF; STAFF403, ẩn danh401. Lỗi theo ApiError, fieldErrors theo filter. JdbcTemplate chuyên repository JDBC; CRUD JPA không thay đổi. Read-only REPEATABLE READ giữ count/page cùng snapshot. Không thêm index/migration khi chưa có bằng chứng cần thiết; các index starts_at, booking showtime/status, booking_seats context đã có. Chưa benchmark dữ liệu lớn, cancellations chưa có index riêng cancelled_at; cần EXPLAIN/đo tải thực tế trước tối ưu thêm.

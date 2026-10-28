# Kết quả kiểm thử backend cuối đợt

Ngày28/10/2026. DB mới cinema_management_test_final_20261028_103848, MySQL8.4.3/3306; import baseline→seed→V001, khôngDROP/reset. `mvnw -Pmysql-it verify`:88test,0failure,0error,0skip. Kết quả máy Kha với JDK25.0.4.1/target21; chưa kiểm chứng JDK21 runtime hoặc máy Đạt.

| Suite | Test | Bằng chứng |
|---|---:|---|
| FoundationErrorHandlingTest |12|health/validation/JSON/lỗi an toàn|
| FoundationMySqlIT |5|mapping/validate/trigger/HTTP|
| SecurityMySqlIT |14|cookie/CSRF/login/me/logout/role|
| CatalogMySqlIT |13|CRUDgenre/movie, links/rollback/filter|
| RoomSeatMySqlIT |12|CRUDphòng/ghế/bulk/rollback/race|
| ShowtimeMySqlIT |13|snapshot/buffer/HATEOAS/3race|
| BookingMySqlIT |15|idempotency/cancel/history/7race|
| StatisticsMySqlIT |4|JDBCtính tay/boundary/empty/null/permission|

Đối chiếu fixtureStats: movieA có2thể loại,2phiếu hiệu lực/3ghế/260000; hủy1phiếu/2ghế/180000 vào00:00ngàyD; sự kiện23:59:59ngàyD-1 không vàoD. Suất23:00đến01:00thuộc ngày bắt đầu. Occupancy3/4=75%; snapshot2cho150% vàanomaly=true, khôngclamp; snapshotnullquákhứ→null; capacity0→null. Các fixture SQL bao gồm trường hợp legacy/bất thường vượt validation API để kiểm tra xử lý dữ liệu; không phải dữ liệu kinh doanh thật.

JAR đóng gói cũng chạy27bước API (genre/movie/room/ghế/lịch/booking/replay/hủy/phân quyền/stats/logout), trên cổng riêng. Minh chứng JSON và2ảnh API thực tế trong reports/evidence. Không có ảnhfrontend giả, không ghi credential/headercookie/token. Chưa chạy collection trực tiếp trong Postman; không dùng các bước tuần tự để chứng minh concurrency.

Nguồn chứng cứ: reports/evidence/test-summary.json, packaged-api-evidence.json, booking-concurrency.json, showtime-concurrency.json, statistics-evidence.json. Các JSON concurrency ghi physicalCONNECTION_ID, status và dữ liệu cuối; anomaly/partial/duplicate kiểm tra0 theo loại ca. Dữ liệu test lịch sử được giữ bởi trigger, tài khoản test vô hiệu hóa; không sửa DB thật.

Lỗi đã xử lý ở các bước trước: đọc association chỉ đọc sau insert chuyển sang projection; giữ offset gốc +07 ở DTO lịch; bỏ controller giả khi API thật có. Lượt cuối không phát hiện lỗi backend mới sau các ca trên. Hạn chế còn lại: staticrỗng, chưaUI/máyĐạt, capacityhistoricalmissing, chưa tải lớn; các chính sách đề xuất chờKha. Không đánh dấu cả dự án hoàn thành từ build.

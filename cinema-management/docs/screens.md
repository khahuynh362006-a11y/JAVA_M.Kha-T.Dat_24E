# Danh sách màn hình bàn giao frontend quản lý rạp phim

Ngày soạn: 27/10/2026. Người duyệt nghiệp vụ: Kha. Người triển khai frontend sau: Đạt.

## 1. Phạm vi và mức xác nhận

Đọc cùng [business-rules.md](business-rules.md). Đây là bản định hướng màn hình, chưa phải frontend đã làm hoặc hợp đồng API đang chạy. README và `docs/api.md` hiện trống; cấu trúc có các trang HTML trong `src/main/resources/static/`. Không tìm thấy AGENTS.md khi đối chiếu. Không đổi cấu trúc hoặc tạo lại dự án.

**Đã xác nhận:** một rạp nhiều phòng; ADMIN quản lý toàn bộ; STAFF xem lịch, tạo/tra cứu đặt chỗ; chưa có khách tự đặt, thanh toán online, đồ ăn, khuyến mãi. Kha làm backend trước, Đạt triển khai sau theo tài liệu bàn giao.

**Đề xuất chờ Kha duyệt:** cách ghép màn hình vào các file HTML hiện có, quyền chi tiết, trạng thái, trường hiển thị bổ sung và các màn hình hỗ trợ dưới đây. Nhãn “MVP bắt buộc” là danh sách dự kiến cần hoàn thành nếu Kha duyệt D-01, không phải các trang đã được hai người chốt trước đây. Form/chi tiết dùng hộp thoại hoặc vùng trong trang để giảm số trang.

Dữ liệu cần từ backend mô tả ý nghĩa nghiệp vụ; chưa chốt tên trường, method, URL hay JSON. Chỉ dùng mock có ghi rõ khi chưa có API; dữ liệu dùng demo nghiệm thu phải từ backend thật.

## 2. Danh sách tổng quát và thứ tự bàn giao

Toàn bộ cách tổ chức trong bảng là **Đề xuất chờ Kha duyệt (D-01/D-02)**. Các đường dẫn là vị trí đã có trong cây, không yêu cầu tạo thêm file lần này.

| Mã | Màn hình | Vị trí dự kiến | Vai trò | Phạm vi |
|---|---|---|---|---|
| SC-01 | Đăng nhập và tài khoản hiện tại | `login.html`, thanh tài khoản chung | ADMIN, STAFF | MVP bắt buộc |
| SC-02 | Tổng quan | `admin/dashboard.html` | ADMIN | MVP bắt buộc, số liệu cơ bản |
| SC-03 | Quản lý thể loại | `admin/genres.html` | ADMIN | MVP bắt buộc |
| SC-04 | Quản lý phim và chi tiết/form phim | `admin/movies.html` | ADMIN | MVP bắt buộc |
| SC-05 | Quản lý phòng | `admin/rooms.html` | ADMIN | MVP bắt buộc |
| SC-06 | Quản lý sơ đồ ghế | `admin/seats.html` | ADMIN | MVP bắt buộc |
| SC-07 | Lịch chiếu theo ngày/phòng; form suất | `admin/showtimes.html` | ADMIN quản lý; STAFF chỉ xem | MVP bắt buộc |
| SC-08 | Chọn suất, chọn ghế, xác nhận kết quả | Vùng tạo đặt chỗ trong `admin/bookings.html` | ADMIN, STAFF | MVP bắt buộc |
| SC-09 | Tra cứu, danh sách, chi tiết và hủy đơn | Vùng tra cứu trong `admin/bookings.html` | ADMIN, STAFF theo quyền | MVP bắt buộc |
| SC-10 | Quản lý tài khoản | `admin/users.html` | ADMIN | MVP bắt buộc, tạo/khóa/cấp quyền |
| SC-11 | Thống kê | `admin/statistics.html` | ADMIN | MVP bắt buộc, báo cáo đơn giản |
| SC-12 | Điều hướng, hết phiên, không đủ quyền, không tìm thấy | `index.html`, layout và thông báo chung | ADMIN, STAFF | MVP bắt buộc, không cần HTML mới |

Thứ tự Kha bàn giao dự kiến: dữ liệu dùng chung và xác thực -> thể loại/phim -> phòng/ghế -> lịch chiếu -> đặt chỗ/tra cứu/hủy -> thống kê. Đạt có thể dựng layout sau khi nhận bản duyệt; không cần tự đoán API hoặc yêu cầu Kha làm thêm module chỉ để khớp mock.

## 3. Quy ước dùng chung cho tất cả màn hình

**Đề xuất chờ Kha duyệt (D-01/D-02):**

- Hiển thị ngày và giờ Việt Nam; suất qua nửa đêm ghi cả ngày kết thúc. Tiền có đơn vị VND, định dạng phân cách hàng nghìn.
- Backend trả quyền/hành động hợp lệ và lý do bị chặn; frontend thể hiện rõ nhưng backend vẫn kiểm tra lại mỗi thao tác.
- Tải: có chỉ báo và khóa thao tác đang phụ thuộc dữ liệu chưa tải. Rỗng: phân biệt chưa có dữ liệu với bộ lọc không có kết quả. Lỗi: có thông báo và nút thử tải lại cho thao tác đọc.
- Khi lưu: khóa nút gửi, giữ dữ liệu form nếu lỗi, hiển thị lỗi từng trường và lỗi nghiệp vụ riêng. Không coi mạng lỗi là chắc chắn chưa lưu; không tự gửi lại thao tác tạo/hủy.
- Phân trang/tìm kiếm ở backend; giữ bộ lọc khi xem chi tiết/quay lại, xử lý trang vượt số trang sau xóa. Kích thước trang và trường sắp xếp chốt ở `api.md` sau.
- Dùng nhãn/biểu tượng cho trạng thái, không chỉ màu. Form có label, lỗi đọc được, điều hướng bàn phím; sơ đồ ghế có trạng thái focus và tên ghế. Kiểm tra ở 360, 768, 1366 px.
- Xóa/hủy/ngừng có xác nhận; nếu bị chặn phải nêu lý do liên kết. Không dựng nút “xóa cưỡng bức”.
- Chưa có thu tiền: dùng “đặt chỗ”, “mã đặt chỗ”, “giá trị đặt chỗ”; không báo đã thanh toán, vé đã soát hoặc doanh thu thực thu.

## 4. Đặc tả từng màn hình MVP

### SC-01 Đăng nhập và tài khoản hiện tại

**Đề xuất chờ Kha duyệt (D-01/D-02/D-11).**

- **Vai trò:** người chưa có phiên; sau đăng nhập ADMIN/STAFF.
- **Hiển thị:** tên đăng nhập, mật khẩu, thông báo kết quả; khi đã vào ứng dụng hiển thị tên tài khoản/vai trò và đăng xuất. Không có đăng ký khách.
- **Thao tác:** đăng nhập, xem phiên hiện tại, đăng xuất; tải lại trang vẫn nhận đúng phiên từ backend. ADMIN vào tổng quan, STAFF vào lịch.
- **Backend cần:** kết quả xác thực, tài khoản hiện tại và quyền, trạng thái phiên/tài khoản; cơ chế phiên/CSRF theo hợp đồng sẽ chốt sau.
- **Tải:** khóa nút đăng nhập trong khi gửi; chưa có kết quả phiên thì chưa hiển thị thao tác quản trị.
- **Rỗng:** form ban đầu; phiên chưa tồn tại thì hiển thị đăng nhập.
- **Lỗi:** sai thông tin hoặc tài khoản không dùng được theo thông báo backend; lỗi mạng giữ tên đăng nhập, không tự gửi lại; hết phiên chuyển về đăng nhập có thông báo.
- **Quy tắc:** không lưu/hiển thị mật khẩu hay hash trong hồ sơ. Liên quan BR-023.

### SC-02 Tổng quan

**Đề xuất chờ Kha duyệt (D-01/D-12).**

- **Vai trò:** ADMIN.
- **Hiển thị:** ngày chọn, số suất chưa hủy, số đặt chỗ CONFIRMED, số ghế được đặt, số đơn hủy và các suất sắp bắt đầu. Ghi rõ số liệu theo ngày bắt đầu suất, không trộn với ngày tạo đơn.
- **Thao tác:** đổi ngày; mở lịch hoặc chi tiết đặt chỗ; không bắt buộc biểu đồ phức tạp.
- **Backend cần:** số liệu tổng hợp, phạm vi thời gian, danh sách suất liên quan, thời điểm cập nhật.
- **Tải:** placeholder cho các số, không hiện 0 như kết quả thật trước khi tải.
- **Rỗng:** ngày không có suất/đơn; hiển thị 0 từ kết quả backend và gợi ý sang lịch.
- **Lỗi:** báo không tải được số liệu, cho thử lại; không thay bằng số giả.
- **Quy tắc:** BR-024; STAFF không có quyền tổng quan toàn rạp theo D-02.

### SC-03 Quản lý thể loại

**Đề xuất chờ Kha duyệt (D-03/D-04).**

- **Vai trò:** ADMIN.
- **Hiển thị:** danh sách mã/tên/mô tả thể loại, phân trang, thông tin đang được phim sử dụng; form thêm/sửa.
- **Thao tác:** tìm tên, thêm, sửa, xóa nếu chưa có liên kết; xác nhận trước xóa.
- **Backend cần:** danh sách phân trang, chi tiết, kết quả validation, thông tin có được xóa và lý do, kết quả lưu.
- **Tải:** chỉ báo danh sách và trạng thái đang lưu form.
- **Rỗng:** chưa có thể loại hoặc không khớp tìm kiếm; nút thêm cho ADMIN.
- **Lỗi:** tên thiếu/sai, ID không tồn tại, dữ liệu đã được phim dùng hoặc đã thay đổi; giữ form và có thể tải lại.
- **Quy tắc:** BR-003/BR-004; danh sách chọn thể loại trong form phim phải lấy từ backend.

### SC-04 Quản lý phim

**Đề xuất chờ Kha duyệt (D-03/D-04).**

- **Vai trò:** ADMIN; STAFF chỉ thấy thông tin phim trong lịch và đặt chỗ, không vào chức năng sửa.
- **Hiển thị:** mã, tên, poster, thời lượng phút, tuổi tối thiểu, thể loại, trạng thái; form có mô tả, URL poster/trailer tùy chọn và chọn nhiều thể loại. Chi tiết nêu khóa sửa thời lượng/tuổi khi có suất chưa kết thúc.
- **Thao tác:** tìm kiếm tên/mã, lọc thể loại/trạng thái, phân trang, xem chi tiết, thêm/sửa/ngừng/kích hoạt, xóa khi được phép.
- **Backend cần:** phim phân trang, bộ lọc thể loại, chi tiết, trạng thái/quyền sửa từng phần, lý do không xóa/ngừng; validation và kết quả lưu.
- **Tải:** tải phim và danh sách thể loại riêng; không cho gửi form khi chưa biết thể loại hợp lệ.
- **Rỗng:** chưa có phim hoặc không khớp bộ lọc; chọn thể loại trống thì gợi ý tạo thể loại trước.
- **Lỗi:** thời lượng/tuổi sai, thể loại không tồn tại, phim đã có suất, thay đổi đồng thời; poster lỗi dùng ảnh thay thế và tên phim, không làm vỡ bố cục.
- **Quy tắc:** BR-002 đến BR-005. Không dùng tuổi trong form như khẳng định đã xác minh tuổi khách.

### SC-05 Quản lý phòng

**Đề xuất chờ Kha duyệt (D-05).**

- **Vai trò:** ADMIN.
- **Hiển thị:** mã/tên phòng, tình trạng sử dụng, số ghế thường/VIP/ngừng sử dụng, có suất liên quan hay không.
- **Thao tác:** thêm/sửa thông tin, kích hoạt/ngừng, xóa phòng mới đủ điều kiện; mở sơ đồ ghế hoặc lịch của phòng.
- **Backend cần:** phòng phân trang, chi tiết/tổng số ghế, tình trạng liên kết và lý do bị chặn, kết quả lưu.
- **Tải:** trạng thái danh sách/form; chưa có thông tin liên kết thì không giả định được xóa.
- **Rỗng:** chưa có phòng hoặc bộ lọc trống; gợi ý tạo phòng rồi thiết lập ghế.
- **Lỗi:** mã trùng, dữ liệu sai, phòng có lịch/đặt chỗ nên không thể xóa/ngừng; không đề nghị xóa dây chuyền.
- **Quy tắc:** BR-006 đến BR-008. Chưa có màn hình lịch bảo trì trong MVP.

### SC-06 Quản lý sơ đồ ghế

**Đề xuất chờ Kha duyệt (D-05).**

- **Vai trò:** ADMIN.
- **Hiển thị:** phòng đang chọn, màn hình chiếu, hàng/số ghế, mã ghế, STANDARD/VIP, ACTIVE/INACTIVE; chú giải và số ghế từng nhóm; thông báo sơ đồ đã bị khóa cấu trúc.
- **Thao tác:** chọn phòng; tạo hàng/số ghế ở phòng chưa có suất; chọn ghế sửa loại/vị trí trước khi khóa; ngừng/kích hoạt khi backend cho phép.
- **Backend cần:** danh sách phòng, đầy đủ tọa độ/hàng/số/loại/trạng thái ghế, trạng thái khóa cấu trúc và lý do; kết quả thay đổi.
- **Tải:** vô hiệu hóa sơ đồ cũ khi đổi phòng cho tới khi có dữ liệu đúng phòng, không cho chọn nhầm ghế.
- **Rỗng:** chưa chọn phòng; phòng mới chưa có ghế; phân biệt với lỗi không tải được.
- **Lỗi:** ghế trùng, sơ đồ bị khóa, ghế có đặt chỗ tương lai, phòng không tồn tại hoặc đã đổi; giữ thao tác chưa lưu khi phù hợp.
- **Quy tắc:** BR-006 đến BR-008; không hiển thị “ghế đã đặt” ở sơ đồ vật lý chung vì đặt chỗ phụ thuộc suất.

### SC-07 Lịch chiếu và quản lý suất

**Đề xuất chờ Kha duyệt (D-06/D-07/D-10).**

- **Vai trò:** ADMIN xem/tạo/sửa/hủy/xóa đủ điều kiện; STAFF chỉ xem và chuyển tới đặt chỗ.
- **Hiển thị:** ngày, bộ lọc phòng/phim; tên phim, phòng, tuổi áp dụng, bắt đầu/kết thúc, khoảng nghỉ 15 phút, giá thường/VIP, trạng thái/giai đoạn và số đặt chỗ bị ảnh hưởng khi hủy. Lịch dạng danh sách theo giờ là đủ MVP.
- **Thao tác:** đổi bộ lọc, xem chi tiết, mở đặt ghế; ADMIN nhập phim/phòng/giờ/giá, sửa phần cho phép, nhập lý do hủy và xác nhận ảnh hưởng; không cần kéo thả lịch.
- **Backend cần:** lịch theo ngày bắt đầu, phim/phòng hoạt động, thời lượng, giá, giờ kết thúc chính thức, giờ server/giai đoạn, quyền/hành động hợp lệ, lý do xung đột và số đơn liên quan.
- **Tải:** đang tải lịch hoặc dữ liệu form; khóa gửi trong khi lưu/hủy.
- **Rỗng:** ngày/phòng chưa có suất; ADMIN được gợi ý tạo, STAFF chỉ nhận thông báo.
- **Lỗi:** trùng lịch, chưa đủ nghỉ, phim/phòng ngừng, giờ quá khứ, suất có đơn nên khóa sửa; giữ form và nêu suất xung đột nếu backend cung cấp. Khi dữ liệu thay đổi trước xác nhận hủy, tải lại thông tin ảnh hưởng và yêu cầu xác nhận mới.
- **Quy tắc:** BR-009 đến BR-014, BR-022; giờ kết thúc UI chỉ là dự kiến. Qua nửa đêm phải ghi ngày kết thúc.

### SC-08 Tạo đặt chỗ và chọn ghế

**Đề xuất chờ Kha duyệt (D-03/D-08/D-10).**

- **Vai trò:** ADMIN, STAFF.
- **Hiển thị:** ngày/suất/phòng/phim/giới hạn tuổi; sơ đồ theo suất, ghế AVAILABLE/BOOKED/UNAVAILABLE và SELECTED cục bộ; loại/giá từng ghế, danh sách chọn, tổng dự kiến; nhắc kiểm tra tuổi tại quầy.
- **Thao tác:** chọn suất, chọn/bỏ ghế, xác nhận kiểm tra tuổi, xem lại đơn và gửi một lần; sau thành công hiển thị mã đặt chỗ, ghế, đơn giá và tổng chính thức, mở chi tiết/tra cứu.
- **Backend cần:** suất còn mở đặt, sơ đồ ghế theo đúng suất, giá/phiên bản giá, trạng thái/hành động, kết quả tạo với mã đơn và giá đã chốt; thông tin ghế xung đột hoặc giá mới khi thất bại.
- **Tải:** chưa có ghế thì không tương tác; khi gửi khóa nút xác nhận và tránh đổi suất làm mất ngữ cảnh.
- **Rỗng:** không có suất còn đặt được; phòng không có ghế sử dụng; hết ghế; chưa chọn ghế là trạng thái hướng dẫn riêng.
- **Lỗi:** ghế vừa bị đặt/ngừng, sai phòng, suất hủy/đã bắt đầu, giá đổi, phiên hết hạn. Tải lại ghế, bỏ lựa chọn không còn hợp lệ; giá mới phải cho xác nhận lại. Timeout báo kết quả chưa rõ, gợi ý tra cứu SC-09; không tự gửi lại.
- **Quy tắc:** BR-015 đến BR-017, BR-020 đến BR-022. SELECTED không giữ ghế; số tiền nhận sau lưu mới là chính thức, không có thông báo “đã thu tiền”.

### SC-09 Tra cứu và hủy đặt chỗ

**Đề xuất chờ Kha duyệt (D-02/D-09/D-11).**

- **Vai trò:** ADMIN tra cứu/hủy mọi đơn hợp lệ; STAFF tra cứu toàn rạp, hủy đơn mình tạo trước bắt đầu.
- **Hiển thị:** ô mã đặt chỗ; danh sách theo suất/ngày bắt đầu/trạng thái/người tạo, có phân trang để tìm lại sau sự cố mạng; chi tiết phim/phòng/giờ/ghế và giá chụp lúc tạo, người/thời điểm tạo, trạng thái, lý do/người/thời điểm hủy và điều kiện được hủy.
- **Thao tác:** tìm mã, lọc danh sách, mở chi tiết; nhập lý do và xác nhận hủy toàn bộ; mở lại sơ đồ suất. Không sửa ghế/giá trong đơn cũ.
- **Backend cần:** danh sách/chi tiết lịch sử, giá đã lưu, hành động được phép và lý do, kết quả hủy; người tạo phải suy ra từ phiên, không do UI tự chọn khi tạo.
- **Tải:** chỉ báo tìm/hủy, khóa nút gửi; không xóa đơn khỏi danh sách trước kết quả server.
- **Rỗng:** không tìm thấy mã hoặc không có kết quả bộ lọc; giữ ô tìm để sửa.
- **Lỗi:** mã không tồn tại, không đủ quyền hủy, đã bắt đầu, đã hủy hoặc trạng thái vừa đổi; hủy lặp hiển thị kết quả đã hủy và không tác động đơn mới. Mất mạng lúc hủy thì đọc lại chi tiết trước khi kết luận.
- **Quy tắc:** BR-018 đến BR-020, BR-023; đơn CANCELLED vẫn tra cứu được. Quyền toàn rạp nhằm đổi ca, không bao gồm quyền sửa đơn người khác.

### SC-10 Quản lý tài khoản

**Đề xuất chờ Kha duyệt (D-01/D-02/D-11).**

- **Vai trò:** ADMIN.
- **Hiển thị:** mã/tên đăng nhập, tên hiển thị, vai trò ADMIN/STAFF, trạng thái hoạt động; không trả/hiện password hash.
- **Thao tác:** tạo tài khoản nhân viên, sửa thông tin/quyền, khóa/kích hoạt. Không có đăng ký công khai, xóa lịch sử hoặc quy trình email khôi phục mật khẩu trong MVP.
- **Backend cần:** danh sách phân trang, vai trò hợp lệ, chi tiết an toàn, kết quả validation/lưu và chính sách quản lý tài khoản được duyệt sau.
- **Tải:** trạng thái tải danh sách/lưu; không hiện dữ liệu quyền cũ khi đang đổi tài khoản.
- **Rỗng:** không có kết quả lọc; gợi ý bỏ lọc/tạo tài khoản khi phù hợp.
- **Lỗi:** trùng tên đăng nhập, thiếu dữ liệu, quyền không hợp lệ, thao tác bị backend chặn; lỗi giữ dữ liệu không nhạy cảm, không hiển thị lại mật khẩu.
- **Quy tắc:** BR-023. Cần chốt bảo vệ ADMIN cuối cùng và cách cấp/đổi mật khẩu trước khi triển khai module này (D-11).

### SC-11 Thống kê

**Đề xuất chờ Kha duyệt (D-12).**

- **Vai trò:** ADMIN.
- **Hiển thị:** khoảng ngày bắt đầu suất được thống kê, số suất/đơn/ghế, tỷ lệ lấp đầy theo suất, phim được đặt nhiều, số đơn hủy, giá trị đặt chỗ; ghi rõ đơn vị và số liệu tạm tính trước giờ chiếu.
- **Thao tác:** lọc từ ngày/đến ngày, theo phòng/phim nếu backend hỗ trợ; xem bảng đơn giản, chưa bắt buộc xuất file/biểu đồ.
- **Backend cần:** dữ liệu tổng hợp và định nghĩa chỉ số, số ghế trong mẫu số, phạm vi ngày, thời điểm cập nhật. Phân trang nếu danh sách suất dài.
- **Tải:** giữ bộ lọc và hiện đang tính, không gán 0 thay lỗi.
- **Rỗng:** không có suất trong khoảng; tỷ lệ mẫu số 0 là “Không áp dụng”, không chia cho 0.
- **Lỗi:** khoảng ngày ngược, không đủ quyền, lỗi truy vấn/mạng; giữ bộ lọc và cho thử lại đọc.
- **Quy tắc:** BR-024; không cộng CANCELLED vào ghế đã đặt hoặc giá trị còn hiệu lực; không gọi giá trị đặt chỗ là doanh thu.

### SC-12 Điều hướng và trạng thái hệ thống

**Đề xuất chờ Kha duyệt (D-01/D-02).**

- **Vai trò:** ADMIN, STAFF.
- **Hiển thị:** menu theo quyền, trang hiện tại, tài khoản, thông báo phiên hết hạn/không đủ quyền/không tìm thấy; đường quay về phù hợp.
- **Thao tác:** chuyển trang, đăng xuất, về đăng nhập/lịch/tổng quan. STAFF truy cập trực tiếp URL quản trị vẫn bị từ chối.
- **Backend cần:** phiên hiện tại, quyền và kết quả lỗi được chuẩn hóa; không tự suy ra quyền từ tham số URL hoặc dữ liệu mock.
- **Tải:** chưa xác định quyền thì không cho thao tác quản trị.
- **Rỗng:** chưa có phiên -> đăng nhập; trang không tồn tại -> thông báo và đường quay lại.
- **Lỗi:** phiên hết hạn giữa lúc điền form -> báo rõ, không tự gửi lại sau đăng nhập; không đủ quyền -> thông báo riêng, không giả thành dữ liệu rỗng.
- **Quy tắc:** bảng quyền của business-rules; giao diện quản trị dùng chung layout cho cả hai vai trò với hành động phù hợp.

## 5. Phần mở rộng sau MVP

**Đề xuất chờ Kha duyệt cho giai đoạn sau; không đưa vào khối lượng bắt buộc hiện tại.** Các mục đã bị loại theo yêu cầu Kha chỉ được xét lại khi có yêu cầu mở rộng mới.

| Màn hình/nhóm | Vai trò dự kiến | Hiển thị và thao tác | Backend cần khi mở rộng | Tải/rỗng/lỗi cần tính |
|---|---|---|---|---|
| Website phim và khách tự đặt | Khách | Danh mục phim, lịch, tài khoản khách, đặt vé | API công khai/khách và quyền mới | Đang tải; chưa có phim; lỗi phiên/đặt ghế |
| Thanh toán online | Khách/nhân viên được cấp quyền | Phương thức, số tiền, trạng thái, kết quả | Đơn thanh toán và xác nhận từ nhà cung cấp | Đang xử lý; chưa có giao dịch; thất bại/kết quả chưa rõ |
| Đồ ăn, combo, khuyến mãi | Vai trò cần duyệt mới | Danh mục/giá/điều kiện | Module và quy tắc mới | Đang tải; hết hàng/không áp dụng; mã không hợp lệ |
| Vé QR, in và soát vé | STAFF/nhân viên soát vé | Mã vé, lượt sử dụng | Vòng đời vé/soát vé, khác với mã đặt chỗ | Đang xác minh; chưa có vé; đã dùng/không hợp lệ |
| Lịch bảo trì và sơ đồ thay đổi theo thời gian | ADMIN | Khoảng bảo trì, phiên bản sơ đồ | Mô hình hiệu lực theo thời gian | Đang tải; chưa có lịch; xung đột suất/ghế |
| Dashboard nâng cao, xuất báo cáo | ADMIN | Biểu đồ/xuất file | Chỉ số và tác vụ xuất | Đang tạo; không dữ liệu; lỗi xuất |

## 6. Điều kiện để Đạt bắt đầu tích hợp

**Đề xuất chờ Kha duyệt về cách bàn giao:**

1. Kha duyệt nghiệp vụ và ma trận quyền; chốt schema/seed ở nhiệm vụ sau.
2. Kha cung cấp `docs/api.md` cho từng chức năng: dữ liệu đầu vào/đầu ra, phân trang, validation, lỗi quyền/không tồn tại/xung đột, ngày giờ, tiền, giá đổi và dữ liệu lịch sử.
3. Có tài khoản kiểm thử ADMIN/STAFF, dữ liệu hai phòng, phim/thể loại, ghế thường/VIP/ngừng sử dụng và suất đủ cho tình huống hợp lệ/lỗi.
4. Đạt thử một trang CRUD đến database trước; sau đó lịch và chọn ghế. Mỗi form phải chứng minh cả thành công và lỗi nghiệp vụ, không chỉ hiển thị mock.
5. Nghiệm thu luồng: thêm phim -> phòng/ghế -> tạo suất -> đặt nhiều ghế -> tra cứu -> hủy -> đặt lại; thử trùng lịch, trùng ghế, sai quyền và giá đổi. Kiểm thử đồng thời thuộc trách nhiệm backend, UI phải xử lý phản hồi đúng.

## 7. Quyết định cần Kha xác nhận

Mọi dòng dưới đây vẫn là **Đề xuất chờ Kha duyệt**. Chi tiết và ví dụ chuẩn ở mục 8 của [business-rules.md](business-rules.md).

| Mã | Quyết định ảnh hưởng giao diện |
|---|---|
| D-01 | Duyệt danh sách SC-01 đến SC-12, HTML/CSS/JS hiện có, form/chi tiết ghép trong trang; tổng quan/tài khoản/thống kê cơ bản thuộc MVP. |
| D-02 | STAFF tra cứu toàn rạp nhưng chỉ hủy đơn mình tạo; trang đầu STAFF là lịch, ADMIN là tổng quan. |
| D-03 | Tuổi tối thiểu nội bộ 0–18 và xác nhận kiểm tra tuổi tại quầy; ít nhất một thể loại; URL poster/trailer tùy chọn. |
| D-04 | Trạng thái phim ACTIVE/INACTIVE; khóa thời lượng/tuổi khi còn suất chưa kết thúc; thông báo ngừng/xóa có liên kết. |
| D-05 | Phòng/ghế ACTIVE/INACTIVE; khóa sơ đồ/loại sau khi có suất; chưa làm lịch bảo trì, không ngừng ghế có đơn tương lai/đang chiếu. |
| D-06 | Giờ Việt Nam đến phút, qua nửa đêm; đóng đặt từ giờ bắt đầu; hiển thị kết thúc chính thức và 15 phút nghỉ. |
| D-07 | Suất có lịch sử đơn khóa phim/phòng/giờ; ADMIN hủy trước bắt đầu kèm lý do và xác nhận số đơn bị ảnh hưởng. |
| D-08 | CONFIRMED ngay, không giữ ghế tạm; một suất/nhiều ghế; không thu thông tin khách; không tự thử lại tạo sau timeout. |
| D-09 | Hủy toàn bộ trước bắt đầu, lý do bắt buộc; không khôi phục; đơn hủy vẫn tra cứu và không có nút hủy một phần. |
| D-10 | Hai giá thường/VIP theo suất; VIP >= thường; hiển thị VND; giá đổi phải xác nhận lại, kết quả lấy giá chốt từ server. |
| D-11 | Giữ dữ liệu lịch sử; chỉ khóa tài khoản, không xóa. Trước khi triển khai tài khoản cần Kha duyệt bảo vệ ADMIN cuối cùng và quy trình cấp/đổi mật khẩu. |
| D-12 | Chỉ số theo ngày bắt đầu suất, phân biệt đơn/ghế/hủy/giá trị; lấp đầy tạm tính trước giờ chiếu và mẫu số chốt khi bắt đầu; không gọi doanh thu thực thu. |

Kha có thể duyệt toàn bộ phương án hoặc chỉ rõ mã cần đổi. Sau duyệt, cập nhật đồng bộ hai tài liệu trước khi dùng làm đầu vào thiết kế CSDL/API; không cần chờ Đạt xác nhận bản đầu này.

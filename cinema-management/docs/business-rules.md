# Nghiệp vụ ban đầu hệ thống quản lý rạp phim

Ngày soạn: 27/10/2026. Người duyệt: Kha. Trạng thái: bản đầu tiên để duyệt trước khi thiết kế CSDL.

## 1. Căn cứ và cách đọc

Đã đối chiếu cấu trúc `cinema-management`, README, `docs/api.md`, bản tổng hợp môn Java ở `../../docs/java-course-review/TONG_HOP_TAI_LIEU_JAVA_VA_DO_AN_RAP_PHIM.md` và nội dung ba tài liệu phân công Kha, Đạt, quy tắc AI được cung cấp trong cuộc trao đổi. Không tìm thấy AGENTS.md trong dự án và các thư mục cha đã kiểm tra. README, API và các tệp nền tảng hiện còn trống; thư mục chưa là Git repository. Không có API, schema hay chức năng đang chạy để coi là chuẩn triển khai.

- **Đã xác nhận:** phạm vi và tám quy tắc nền tảng do Kha nêu trong yêu cầu hiện tại.
- **Đề xuất chờ Kha duyệt:** mọi lựa chọn cụ thể bổ sung, gồm tên trạng thái, quyền chi tiết, cách hủy, giá VIP, điều kiện sửa và chính sách dữ liệu. Chúng chưa phải quyết định đã chốt.
- Các mục “tiêu chí kiểm tra” là tiêu chí nghiệm thu dự kiến, chưa phải kết quả đã kiểm thử.
- Tài liệu này mô tả nghiệp vụ. Tên trường, URL, JSON và mã lỗi cụ thể sẽ được chốt trong `docs/api.md` ở nhiệm vụ sau, không coi mô tả dữ liệu dưới đây là API đã tồn tại.
- `screens.md` là bản giao việc giao diện tương ứng. Quyết định D-01 đến D-12 ở cuối hai tài liệu dùng chung mã để duyệt một lần.

## 2. Mục tiêu và phạm vi MVP

**Đã xác nhận:** quản lý một rạp có nhiều phòng; ADMIN quản lý toàn bộ; STAFF xem lịch, tạo và tra cứu đặt chỗ tại quầy. Backend do Kha làm trước, frontend do Đạt làm sau theo tài liệu bàn giao.

Nghiệp vụ chính: quản lý phim và thể loại, phòng và ghế, lập lịch chiếu, chọn ghế và xác nhận đặt chỗ, tra cứu và hủy hợp lệ. Mỗi suất có phim, phòng, thời gian và giá; giữ đúng dữ liệu lịch sử khi đã phát sinh đặt chỗ.

**Ngoài MVP đã xác nhận:** khách tự đặt vé, thanh toán trực tuyến, đồ ăn, khuyến mãi. MVP không quản lý thu tiền, hoàn tiền hoặc doanh thu thực thu. Mã đặt chỗ phục vụ tra cứu, chưa có nghiệp vụ soát vé/QR.

**Đề xuất chờ Kha duyệt (D-01):** giao diện HTML/CSS/JavaScript trong `src/main/resources/static/` theo phân công hiện có; thêm quản lý tài khoản ADMIN/STAFF, tổng quan và thống kê cơ bản phù hợp cây thư mục. Không thêm chức năng chỉ vì slide môn học có React, dịch vụ thanh toán hoặc khách hàng.

## 3. Bảng phân quyền dự kiến

ADMIN quản lý toàn bộ và STAFF được xem lịch/tạo/tra cứu đặt chỗ là **Đã xác nhận**. Cách chia quyền từng hành động dưới đây là **Đề xuất chờ Kha duyệt (D-02)**. Quyền phải được kiểm tra tại backend; ẩn nút không thay thế kiểm tra quyền.

| Chức năng | ADMIN | STAFF |
|---|---|---|
| Đăng nhập, đăng xuất, xem tài khoản hiện tại | Có | Có |
| Xem phim, thể loại, thông tin phòng/ghế cần cho đặt chỗ | Có | Chỉ đọc trong luồng lịch/đặt chỗ |
| Thêm/sửa/ngừng sử dụng phim và thể loại | Có, theo ràng buộc dữ liệu | Không |
| Quản lý phòng, sơ đồ, loại ghế, trạng thái sử dụng | Có, theo ràng buộc dữ liệu | Không |
| Tạo/sửa/hủy suất chiếu | Có, theo BR-010 đến BR-014 | Không |
| Xem lịch, chi tiết suất và ghế | Có | Có |
| Tạo đặt chỗ tại quầy | Có | Có |
| Tra cứu đặt chỗ theo mã, xem danh sách/chi tiết | Toàn bộ | Toàn bộ trong rạp để hỗ trợ đổi ca |
| Hủy đặt chỗ trước giờ chiếu | Mọi đặt chỗ hợp lệ | Chỉ đặt chỗ do mình tạo |
| Sửa ghế/suất/giá của đặt chỗ đã xác nhận | Không; hủy hợp lệ rồi tạo mới | Không; hủy hợp lệ rồi tạo mới |
| Quản lý tài khoản và vai trò | Có | Không |
| Xem tổng quan, thống kê toàn rạp | Có | Không; trang đầu STAFF là lịch chiếu |
| Xóa lịch sử đặt chỗ | Không có thao tác trong MVP | Không |

Người chưa đăng nhập chỉ truy cập đăng nhập; phiên hết hạn không được thực hiện thao tác dữ liệu. Không có đăng ký công khai. Cơ chế cookie/CSRF đã được đề xuất trong phân công, nhưng lựa chọn kỹ thuật xác thực nằm ngoài phạm vi chốt nghiệp vụ lần này.

## 4. Quy tắc nghiệp vụ

### 4.1 Phạm vi, phim và thể loại

| Mã | Nội dung và mức xác nhận | Ví dụ hợp lệ / không hợp lệ | Tiêu chí kiểm tra |
|---|---|---|---|
| BR-001 | **Đã xác nhận:** dữ liệu thuộc một rạp, có nhiều phòng. Không có khách tự đặt và thanh toán online trong MVP. | Hợp lệ: nhân viên chọn phòng 1; không hợp lệ: luồng khách tự thanh toán được coi là chức năng MVP. | Demo xuyên suốt bằng ADMIN/STAFF, không yêu cầu cổng thanh toán. |
| BR-002 | **Đã xác nhận:** phim có thời lượng, giới hạn tuổi, trạng thái hoạt động. **Đề xuất chờ Kha duyệt (D-03):** tên không trắng; thời lượng là số phút nguyên dương; tuổi tối thiểu là số nguyên 0–18, 0 nghĩa không giới hạn theo quy ước nội bộ; không coi đây là bộ mã phân loại pháp lý. | Hợp lệ: 120 phút, tuổi 16; không hợp lệ: 0 phút, tuổi -1 hoặc thiếu tên. | Chặn dữ liệu sai ở backend, thông báo từng trường; biên 1 phút, tuổi 0/18 đều được kiểm tra. |
| BR-003 | **Đề xuất chờ Kha duyệt (D-03):** một phim có ít nhất một thể loại tồn tại; nhiều thể loại không lặp. Mã phim duy nhất; không dùng riêng tên phim làm khóa vì phim có thể trùng tên. Poster/trailer là URL tùy chọn; chưa upload media. | Hợp lệ: phim gắn hai thể loại khác nhau; không hợp lệ: thể loại không tồn tại hoặc cùng ID lặp. | Liên kết thiếu bị từ chối; không tạo phim/quan hệ dở dang; tìm kiếm/phân trang không lặp phim do JOIN. |
| BR-004 | **Đề xuất chờ Kha duyệt (D-04):** chỉ xóa phim chưa từng có suất; chỉ xóa thể loại chưa được phim dùng. Dữ liệu có liên kết chuyển sang ngừng sử dụng hoặc gỡ liên kết hợp lệ, không xóa dây chuyền. Không ngừng phim nếu còn suất chưa hủy có giờ kết thúc ở tương lai. | Hợp lệ: xóa phim nhập thử chưa có suất; không hợp lệ: xóa phim có suất cũ, hoặc ngừng phim đang có suất tương lai. | Thao tác bị chặn nêu dữ liệu đang liên kết; ADMIN xử lý suất trước; lịch sử không mất. |
| BR-005 | **Đề xuất chờ Kha duyệt (D-04):** khi phim có suất chưa hủy chưa kết thúc, khóa sửa thời lượng/giới hạn tuổi. Mỗi suất lưu thời lượng/tuổi áp dụng lúc tạo; sửa phim sau này không âm thầm đổi thời gian hay dữ liệu lịch sử của suất cũ. | Hợp lệ: sửa mô tả; không hợp lệ: đổi 120 thành 150 phút khi có suất tương lai. | Sau sửa phim, giờ kết thúc của suất đã tạo không đổi; thông báo vì sao trường bị khóa. |

Độ tuổi là thông tin để nhân viên kiểm tra tại quầy. **Đề xuất chờ Kha duyệt (D-03):** MVP hiển thị và yêu cầu nhân viên xác nhận đã kiểm tra độ tuổi; chưa thu ngày sinh/giấy tờ khách hay tự động kiểm chứng tuổi.

### 4.2 Phòng và ghế

| Mã | Nội dung và mức xác nhận | Ví dụ hợp lệ / không hợp lệ | Tiêu chí kiểm tra |
|---|---|---|---|
| BR-006 | **Đã xác nhận:** mỗi phòng có sơ đồ riêng, ghế thường/VIP và trạng thái sử dụng. **Đề xuất chờ Kha duyệt (D-05):** mã phòng duy nhất; mã ghế duy nhất trong từng phòng; số ghế dương; hàng chữ và số xác định vị trí. | Hợp lệ: A1 ở hai phòng khác nhau; không hợp lệ: hai ghế A1 cùng phòng. | Đổi phòng tải đúng sơ đồ; backend kiểm tra đúng phòng chứ không chỉ trùng tên ghế. |
| BR-007 | **Đã xác nhận:** không tùy tiện xóa phòng/ghế đã có đặt chỗ. **Đề xuất chờ Kha duyệt (D-05):** chỉ xóa phòng chưa từng có suất; chỉ xóa ghế khi chưa có suất nào của phòng. Sau khi phòng đã có suất, khóa thêm/xóa/đổi vị trí/loại ghế; chỉ đổi trạng thái sử dụng theo BR-008. Phòng ngừng sử dụng được giữ lịch sử. | Hợp lệ: sửa sơ đồ phòng mới; không hợp lệ: xóa A1 có đặt chỗ, đổi VIP thành thường trong phòng đã có suất. | Không có thao tác tạo lại sơ đồ làm mất ghế cũ; lỗi giải thích liên kết; không xóa dây chuyền. |
| BR-008 | **Đề xuất chờ Kha duyệt (D-05):** chỉ ngừng phòng nếu không còn suất chưa hủy chưa kết thúc. Chỉ ngừng ghế nếu không có đặt chỗ CONFIRMED trên suất chưa kết thúc; ADMIN xử lý các đặt chỗ liên quan trước. Ghế ngừng sử dụng không được đặt mới. Không có lịch bảo trì theo khoảng trong MVP. | Hợp lệ: ngừng A2 chưa ai đặt; không hợp lệ: ngừng A1 đang có đặt chỗ suất tối nay. | Kiểm tra lại liên kết ngay khi lưu; không nhận đặt mới cùng lúc ghế bị ngừng. |

### 4.3 Suất chiếu và khoảng nghỉ

| Mã | Nội dung và mức xác nhận | Ví dụ hợp lệ / không hợp lệ | Tiêu chí kiểm tra |
|---|---|---|---|
| BR-009 | **Đã xác nhận:** suất có phim, phòng, bắt đầu, kết thúc, giá; kết thúc tính từ thời lượng phim. **Đề xuất chờ Kha duyệt (D-06):** dùng giờ Asia/Ho_Chi_Minh; nhập đến phút, giây bằng 0; bắt đầu phải ở tương lai theo đồng hồ server; phim/phòng hoạt động và phòng có ghế sử dụng được. | Hợp lệ: bắt đầu 18:00, phim 120 phút, kết thúc 20:00; không hợp lệ: client tự gửi kết thúc 19:00 hoặc suất bắt đầu ở quá khứ. | Backend tính lại thời gian và kiểm tra dữ liệu liên quan; lịch qua nửa đêm giữ đủ ngày. |
| BR-010 | **Đã xác nhận:** cùng phòng không trùng suất, nghỉ tối thiểu 15 phút. Với A trước B: `B.start >= A.end + 15 phút`; khi thêm/sửa phải kiểm tra cả suất trước và sau. Thời gian kết thúc phim không bao gồm thời gian nghỉ. | Hợp lệ: A 18:00–20:00, B 20:15–22:00; không hợp lệ: B bắt đầu 20:14. Cùng giờ ở phòng khác là hợp lệ. | Kiểm tra sát biên 20:14/20:15, trường hợp bao trùm, chèn giữa hai suất, sửa suất không tự so với chính nó; suất hủy không chiếm lịch. |
| BR-011 | **Đã xác nhận:** chống trùng lịch cả khi hai yêu cầu đồng thời. **Đề xuất chờ Kha duyệt (D-06):** hiệu lực bảo vệ bao gồm tạo/sửa lịch, đổi trạng thái phòng/phim và thay đổi dữ liệu ảnh hưởng điều kiện hợp lệ. | Hợp lệ: hai request cùng phòng nhưng hai khoảng đủ nghỉ; không hợp lệ: hai request khoảng trùng đều lưu thành công. | Gửi đồng thời hai yêu cầu trùng vào phòng đang chưa có suất: chỉ một thành công; trạng thái cuối cùng không có lịch trùng, không chỉ kiểm tra ở UI. |
| BR-012 | **Đề xuất chờ Kha duyệt (D-07):** trước giờ bắt đầu, suất SCHEDULED chưa từng có đặt chỗ được sửa phim/phòng/giờ và phải kiểm tra lại toàn bộ. Đã có bất kỳ lịch sử đặt chỗ, kể cả đã hủy: khóa phim/phòng/giờ. Chỉ xóa vật lý suất chưa từng có đặt chỗ và chưa bắt đầu; trường hợp khác dùng hủy. Giá theo BR-022. | Hợp lệ: dời suất nhập nhầm chưa có đặt chỗ; không hợp lệ: đổi phòng suất đã có mã đặt chỗ, kể cả tất cả đã hủy. | Backend chặn sửa/xóa liên kết; kết quả đồng thời tạo đặt chỗ và sửa suất phải tuân một thứ tự hợp lệ, không có đơn trỏ tới suất bị đổi trái quy tắc. |
| BR-013 | **Đề xuất chờ Kha duyệt (D-07):** chỉ ADMIN hủy suất SCHEDULED trước giờ bắt đầu, bắt buộc lý do. Nếu có đặt chỗ CONFIRMED, UI phải hiển thị số lượng bị ảnh hưởng và yêu cầu xác nhận; backend hủy suất và toàn bộ các đặt chỗ còn hiệu lực trong cùng thao tác toàn vẹn, giữ lịch sử và lý do hệ thống. | Hợp lệ: ADMIN xác nhận hủy suất 20:00 lúc 18:00; không hợp lệ: hủy suất đã bắt đầu, hoặc chỉ hủy nửa số đặt chỗ rồi báo thành công. | Chỉ trả thành công khi suất và các đặt chỗ đều đã hủy; đặt chỗ đồng thời không được tồn tại hiệu lực trên suất đã hủy. Đây không phải luồng hoàn tiền. |
| BR-014 | **Đề xuất chờ Kha duyệt (D-06/D-07):** trạng thái không cho phép đặt khi server time >= giờ bắt đầu; không sửa/hủy/xóa suất đã bắt đầu; suất đã kết thúc chỉ đọc. Suất qua ngày được phép; lọc lịch theo ngày bắt đầu và hiển thị ngày kết thúc nếu khác ngày. | Hợp lệ: suất 23:00 ngày 27 kết thúc 01:00 ngày 28; không hợp lệ: đặt lúc đúng 23:00. | Kiểm tra trước/đúng/sau giờ bắt đầu, sát nửa đêm; không dùng giờ máy nhân viên làm căn cứ. |

### 4.4 Đặt chỗ và hủy

| Mã | Nội dung và mức xác nhận | Ví dụ hợp lệ / không hợp lệ | Tiêu chí kiểm tra |
|---|---|---|---|
| BR-015 | **Đề xuất chờ Kha duyệt (D-08):** một đặt chỗ cho một suất, ít nhất một ghế; thành công là CONFIRMED ngay. Chưa có PENDING, giữ ghế tạm hay hết hạn giữ chỗ; chọn ghế trên UI không chiếm ghế ở server. Mã đặt chỗ duy nhất, do server cấp; ghi người tạo và thời điểm. Không bắt buộc thông tin cá nhân khách trong MVP. | Hợp lệ: một đơn gồm A1/A2 cùng suất; không hợp lệ: đơn rỗng hoặc gom ghế của hai suất. | Chỉ có đơn khi toàn bộ thao tác lưu thành công; hai nhân viên vẫn có thể chọn cùng ghế trên UI nhưng chỉ một người xác nhận được. |
| BR-016 | **Đã xác nhận:** một ghế chỉ có một đặt chỗ còn hiệu lực trên cùng suất. **Đề xuất chờ Kha duyệt (D-08):** còn hiệu lực nghĩa là CONFIRMED; ghế phải thuộc phòng của suất, đang sử dụng, suất còn cho đặt. Cùng ghế ở suất khác được đặt độc lập. | Hợp lệ: A1 suất 18:00 và A1 suất 20:15; không hợp lệ: A1 suất 18:00 có hai đơn CONFIRMED. | Hai request đồng thời cùng ghế: đúng một thành công; dữ liệu lưu cũng duy trì điều kiện này, không dựa vào tải lại sơ đồ. |
| BR-017 | **Đề xuất chờ Kha duyệt (D-08):** đặt nhiều ghế là toàn bộ hoặc không ghế nào. Không cho ID ghế lặp. Sau lỗi không lưu đơn rỗng/thiếu ghế, không chiếm các ghế còn lại. | Hợp lệ: A1/A2 đều trống thì lưu cả hai; không hợp lệ: A2 đã đặt nhưng vẫn lưu A1. | Tạo request có một ghế xung đột: số đơn hiệu lực không tăng, mọi ghế còn lại vẫn trống; frontend giữ lựa chọn còn hợp lệ và báo ghế xung đột. |
| BR-018 | **Đã xác nhận:** hủy hợp lệ phải giải phóng ghế và giữ lịch sử. **Đề xuất chờ Kha duyệt (D-09):** chỉ hủy toàn bộ đơn CONFIRMED trước giờ bắt đầu, theo quyền bảng trên; bắt buộc lý do, người hủy, thời điểm. Không hủy một phần ghế hoặc khôi phục đơn đã hủy. | Hợp lệ: hủy A1/A2 lúc 17:30 cho suất 18:00; không hợp lệ: hủy lúc 18:00, hủy riêng A1 hoặc STAFF hủy đơn người khác. | Tất cả ghế được đặt lại sau hủy; đơn cũ vẫn tra được đủ chi tiết/giá, không đổi thành đơn mới khi ghế được đặt lại. |
| BR-019 | **Đề xuất chờ Kha duyệt (D-09):** hủy lặp đơn đã CANCELLED được trả lại kết quả đã hủy, không thêm lần hủy hay giải phóng nhầm ghế của đơn mới. Request hủy đầu tiên vẫn phải kiểm tra quyền và thời gian; đọc lại đơn cũ không tạo hành động mới. | Hợp lệ: hủy B1, đặt lại ghế bằng B2, gửi lại hủy B1 thì B2 còn nguyên; không hợp lệ: request hủy lặp làm mất B2. | Kiểm tra lặp và đồng thời hủy/đặt lại; lịch sử có đúng một lần chuyển CONFIRMED sang CANCELLED. |
| BR-020 | **Đề xuất chờ Kha duyệt (D-08):** không sửa ghế/suất trong đơn đã xác nhận; hủy hợp lệ rồi tạo đơn mới. Lỗi/mất kết nối khi xác nhận không có nghĩa chắc chắn thất bại; tra cứu kết quả trước khi gửi lại. Chưa có cơ chế tự động thử lại thao tác tạo ở frontend. | Hợp lệ: tra cứu sau timeout; không hợp lệ: tự báo đã hủy hoặc tự gửi lại nhiều lần dù chưa biết kết quả. | UI phân biệt lỗi xác định và kết quả chưa rõ; backend không tạo hai đơn cùng ghế đang hiệu lực. Hợp đồng chống lặp request tạo sẽ được chốt khi thiết kế API. |

### 4.5 Giá, lịch sử và thống kê

| Mã | Nội dung và mức xác nhận | Ví dụ hợp lệ / không hợp lệ | Tiêu chí kiểm tra |
|---|---|---|---|
| BR-021 | **Đã xác nhận:** giá và tổng do backend tính. **Đề xuất chờ Kha duyệt (D-10):** mỗi suất có giá thường và giá VIP riêng, VND nguyên không âm, VIP >= thường; không nhân hệ số hoặc tự tính phụ phí. Tổng bằng tổng đơn giá ghế; client chỉ gửi lựa chọn và thông tin phiên bản giá để kiểm tra thay đổi. | Hợp lệ: thường 80.000, VIP 100.000; A1 thường + B1 VIP = 180.000; không hợp lệ: client gửi tổng 1.000 để thay tổng thực. | Chặn âm, lẻ VND, VIP thấp hơn thường; sửa giá trên request không đổi giá backend; không có phí ẩn. |
| BR-022 | **Đã xác nhận:** đơn đã tạo giữ giá thời điểm tạo. **Đề xuất chờ Kha duyệt (D-10):** ADMIN được đổi giá suất chưa bắt đầu; chỉ áp dụng đặt mới. Lưu đơn giá từng ghế, loại/mã ghế và tổng lúc tạo. Khi giá đổi sau khi UI đã tải, từ chối xác nhận theo giá cũ, trả thông tin mới để nhân viên xác nhận lại; không âm thầm nhận giá tăng. | Hợp lệ: đơn B1 giá 80.000 giữ nguyên, B2 sau đổi giá là 90.000; không hợp lệ: B1 hiển thị lại thành 90.000 hoặc UI báo 80.000 nhưng backend nhận 90.000 không thông báo. | Kiểm tra tạo đơn trước/sau/đồng thời đổi giá; một đơn dùng cùng một phiên bản giá; lịch sử không tính lại bằng bảng giá hiện tại. |
| BR-023 | **Đề xuất chờ Kha duyệt (D-11):** tra cứu lịch sử dùng định danh ổn định và thông tin chụp tại thời điểm tạo: mã đơn, suất/phim/phòng, ghế/loại ghế, đơn giá/tổng, người tạo, trạng thái và thông tin hủy. Không xóa tài khoản đã phát sinh hoạt động; khóa tài khoản, giữ người thao tác. Mọi thao tác phá liên kết phải bị chặn ở backend. | Hợp lệ: đổi tên hiển thị phim không làm mất thông tin đơn cũ; không hợp lệ: xóa user khiến lịch sử mất người tạo. | Đơn cũ vẫn đọc được sau đổi tên/ngừng đối tượng; không có thao tác xóa lịch sử trong UI. |
| BR-024 | **Đề xuất chờ Kha duyệt (D-12):** thống kê theo suất có ngày bắt đầu trong khoảng chọn (bao gồm hai ngày biên), giờ Việt Nam. Ghế đã đặt chỉ đếm CONFIRMED; đặt chỗ hủy thống kê riêng. Tỷ lệ lấp đầy = ghế CONFIRMED / sức chứa sử dụng của suất, mẫu số chốt lúc suất bắt đầu; trước đó dùng số ghế khả dụng hiện tại, hiển thị là tạm tính. Suất CANCELLED loại khỏi lấp đầy/xếp hạng, vẫn đếm hủy riêng. | Hợp lệ: 8 ghế xác nhận / 10 ghế sử dụng = 80%; không hợp lệ: tính đơn hủy vào số ghế đặt hoặc gọi tổng giá trị là tiền đã thu. | Mẫu số 0 hiển thị “Không áp dụng”; phân biệt số đơn/số ghế; báo “giá trị đặt chỗ”, không “doanh thu thực thu”; dữ liệu trống không sinh tỷ lệ giả. |

## 5. Trạng thái và chuyển trạng thái dự kiến

**Toàn bộ tên và cơ chế trạng thái trong mục này là Đề xuất chờ Kha duyệt (D-04 đến D-09).** Trạng thái ghế khả dụng theo từng suất được phân biệt với tình trạng vật lý của ghế.

| Đối tượng | Trạng thái dự kiến | Chuyển được phép | Điều kiện và giới hạn |
|---|---|---|---|
| Phim | ACTIVE / INACTIVE | Tạo ACTIVE hoặc INACTIVE; đổi hai chiều | ACTIVE mới được xếp suất; chuyển INACTIVE theo BR-004. Thời lượng/tuổi theo BR-005. |
| Phòng | ACTIVE / INACTIVE | Tạo ACTIVE hoặc INACTIVE; đổi hai chiều | Ngừng theo BR-008; kích hoạt không tự mở lại suất đã hủy. Chưa có trạng thái lịch bảo trì riêng. |
| Ghế vật lý | ACTIVE / INACTIVE; loại STANDARD / VIP tách riêng | Đổi sử dụng hai chiều theo BR-008 | Không đổi loại/sơ đồ sau khi phòng đã có suất theo BR-007. |
| Suất chiếu | SCHEDULED / CANCELLED / COMPLETED | Tạo SCHEDULED; SCHEDULED -> CANCELLED; SCHEDULED -> COMPLETED khi hết phim | CANCELLED kết thúc vĩnh viễn; không khôi phục. COMPLETED là trạng thái hiển thị suy ra từ giờ kết thúc, không cần tác vụ cập nhật nền. |
| Đặt chỗ | CONFIRMED / CANCELLED | Tạo CONFIRMED; CONFIRMED -> CANCELLED | Đơn cũ vẫn CONFIRMED sau khi phim kết thúc, không tự trở thành đã thanh toán/đã sử dụng. CANCELLED không khôi phục. |

Suất SCHEDULED có hai giai đoạn suy ra theo giờ server: **chưa bắt đầu** (được đặt) và **đang chiếu** (không nhận đặt/sửa/hủy). Backend trả trạng thái/giai đoạn và hành động được phép; không để frontend tự kết luận chỉ bằng đồng hồ máy.

Sơ đồ theo suất có: AVAILABLE (trống), BOOKED (có đơn CONFIRMED), UNAVAILABLE (ghế ngừng sử dụng). SELECTED chỉ là lựa chọn cục bộ của frontend, không phải ghế được giữ ở database. Nếu một trạng thái lịch sử tồn tại trước khi ghế ngừng, phải giữ thông tin đơn; quy tắc chuyển ghế hiện tại không được tạo xung đột này.

## 6. Luồng nghiệp vụ dự kiến

### 6.1 Xếp lịch

1. ADMIN chọn phim/phòng hoạt động và thời gian bắt đầu, nhập giá thường/VIP.
2. UI xem trước kết thúc và 15 phút nghỉ; preview không phải kết quả hợp lệ cuối cùng.
3. Backend kiểm tra quyền, dữ liệu mới nhất, thời lượng/tuổi áp dụng, tình trạng phòng/phim, ghế, thời gian và giá.
4. Kiểm tra suất trước/sau trong cùng phòng theo BR-010, bao gồm xung đột đồng thời BR-011.
5. Chỉ lưu nếu toàn bộ hợp lệ; trả suất và giờ kết thúc chính thức. Lỗi giữ form, giải thích điều kiện vi phạm; không tự đổi phòng/giờ giúp người dùng.
6. Khi sửa/hủy áp dụng BR-012/BR-013 và kiểm tra liên kết mới nhất, không chỉ dựa vào số đặt chỗ đã tải trước đó.

### 6.2 Đặt ghế tại quầy

1. ADMIN/STAFF chọn ngày, suất còn cho đặt; xem tên phim, tuổi áp dụng, phòng, giờ, loại ghế, giá và chú giải.
2. Chọn ít nhất một ghế AVAILABLE, xác nhận kiểm tra tuổi tại quầy theo D-03; UI tính tổng dự kiến để hiển thị.
3. Gửi xác nhận; khóa nút khi đang gửi. Backend kiểm tra lại quyền, giờ server, suất/phòng/phim, ghế và phiên bản giá; tính lại giá.
4. Lưu toàn bộ đơn/ghế/giá/lịch sử trong một thao tác toàn vẹn, bảo vệ cả request đồng thời. Chỉ sau lưu thành công mới trả mã đặt chỗ.
5. Nếu ghế xung đột hoặc giá đổi: không tạo đơn, trả nguyên nhân để UI tải lại và nhân viên xác nhận lại. Nếu chưa rõ kết quả do mất mạng: hướng dẫn tra cứu, không tự tạo tiếp.

### 6.3 Hủy đặt chỗ

1. Tra cứu mã, xem trạng thái, giờ chiếu, ghế và tổng đã lưu.
2. Backend xác định được hủy hay không theo quyền, trạng thái và giờ server; UI hiển thị lý do khi không được hủy.
3. Người dùng nhập lý do và xác nhận hủy toàn bộ. Backend kiểm tra lại tại thời điểm thực hiện.
4. Chuyển CANCELLED và giải phóng tất cả ghế trong cùng thao tác; lưu người/lý do/thời điểm. Giữ đơn và chi tiết giá để tra cứu.
5. Tải lại chi tiết và sơ đồ; ghế đặt lại được bằng đơn khác nếu suất còn cho đặt. Hủy lại đơn cũ không ảnh hưởng đơn mới.

Hủy suất bởi ADMIN là luồng riêng có ảnh hưởng nhiều đơn theo BR-013; không cho STAFF gọi thay luồng này.

## 7. Ma trận sửa/xóa dự kiến

**Đề xuất chờ Kha duyệt.** “Có lịch sử” bao gồm đặt chỗ đã hủy. Các thao tác sửa liên quan đồng thời phải kiểm tra lại trạng thái và liên kết khi lưu.

| Đối tượng | Có thể sửa | Khi nào xóa vật lý | Khi đã có liên kết |
|---|---|---|---|
| Thể loại | Tên/mô tả | Chưa phim nào sử dụng | Gỡ liên kết bằng sửa phim, không xóa phim theo thể loại |
| Phim | Nội dung; thời lượng/tuổi theo BR-005 | Chưa từng có suất | Giữ lịch sử, ngừng theo BR-004 |
| Phòng | Tên/mô tả; trạng thái theo BR-008 | Chưa từng có suất | Không đổi mã định danh; không xóa dây chuyền |
| Ghế | Sơ đồ/loại trước khi phòng có suất; trạng thái theo BR-008 | Phòng chưa có suất | Giữ ID/mã/vị trí/loại, không tạo lại sơ đồ |
| Suất | Trước bắt đầu, chưa từng có đặt chỗ; giá riêng theo BR-022 | Chưa bắt đầu, chưa từng có đặt chỗ | Khóa phim/phòng/giờ; hủy theo BR-013 |
| Đặt chỗ | Không sửa ghế/suất/giá; chỉ hủy hợp lệ | Không hỗ trợ | Giữ toàn bộ lịch sử |
| Tài khoản | ADMIN quản lý hồ sơ/quyền/trạng thái | Đề xuất không cung cấp xóa trong MVP | Khóa để ngừng truy cập, giữ tham chiếu người thao tác |

## 8. Quyết định cần Kha duyệt trước thiết kế CSDL

Tất cả các dòng dưới đây là **Đề xuất chờ Kha duyệt**, không có lựa chọn nào được duyệt ngầm do tài liệu đã được tạo.

| Mã | Nội dung cần duyệt | Phương án đơn giản đề xuất | Ảnh hưởng |
|---|---|---|---|
| D-01 | Phạm vi giao diện và chức năng phụ MVP | HTML/CSS/JS sẵn có; tổng quan, tài khoản, thống kê cơ bản cho ADMIN; không thêm React/thanh toán | Danh sách màn hình và khối lượng 2–3 tuần |
| D-02 | Quyền tra cứu/hủy của STAFF | Tra cứu toàn rạp để đổi ca; chỉ hủy đơn mình tạo; ADMIN hủy mọi đơn còn hợp lệ | Bộ lọc, quyền và người tạo đơn |
| D-03 | Phim, thể loại và kiểm tra tuổi | Phút nguyên dương; tuổi tối thiểu 0–18 theo nội bộ; ít nhất một thể loại; nhân viên xác nhận kiểm tra tuổi; poster/trailer URL tùy chọn | Cột dữ liệu, validation và form |
| D-04 | Vòng đời phim và ảnh hưởng sửa phim | ACTIVE/INACTIVE; khóa thời lượng/tuổi khi có suất chưa hủy chưa kết thúc; suất lưu thông tin áp dụng; không ngừng phim còn suất tương lai/đang chiếu | Dữ liệu suất và lịch sử |
| D-05 | Vòng đời phòng/ghế | ACTIVE/INACTIVE; khóa cấu trúc/loại ghế sau khi có suất; không ngừng ghế đang có đơn hiệu lực ở suất chưa kết thúc; chưa có lịch bảo trì riêng | Cấu trúc ghế, sửa/xóa và mẫu số thống kê |
| D-06 | Thời gian và điều kiện mở đặt | Giờ Việt Nam, độ chính xác phút, cho qua nửa đêm; chặn đặt từ đúng giờ bắt đầu; nghỉ đủ 15 phút, bảo vệ đồng thời | Lưu/lọc ngày giờ và kiểm tra trùng |
| D-07 | Sửa/xóa/hủy suất | Khóa phim/phòng/giờ khi đã từng có đơn; ADMIN được hủy trước bắt đầu kèm lý do và hủy toàn bộ đơn hiệu lực một cách toàn vẹn; không khôi phục | Trạng thái và thao tác có ảnh hưởng nhiều đơn |
| D-08 | Vòng đời đặt chỗ và dữ liệu khách | CONFIRMED ngay, không giữ ghế tạm; một suất/nhiều ghế toàn vẹn; chưa thu thông tin khách; không sửa đơn và không tự thử lại tạo sau timeout | Mã đơn, chi tiết ghế, tính toàn vẹn và API sau này |
| D-09 | Hủy đơn | Hủy toàn bộ trước bắt đầu, có lý do/người/thời điểm; CANCELLED không khôi phục; hủy lặp không tạo tác động mới | Lịch sử và giải phóng ghế |
| D-10 | Giá thường/VIP và đổi giá | Hai giá theo suất, VND nguyên không âm, VIP >= thường; đổi giá chỉ áp dụng đơn mới; giá đổi phải xác nhận lại; lưu đơn giá/loại ghế/tổng lúc tạo | Schema giá và giao diện xác nhận |
| D-11 | Lịch sử và tài khoản | Giữ thông tin áp dụng lúc tạo; tài khoản chỉ khóa, không xóa; lưu người thao tác | Quan hệ và dữ liệu lịch sử |
| D-12 | Thống kê MVP | Theo ngày bắt đầu suất; chỉ CONFIRMED vào số ghế/giá trị; hủy riêng; tỷ lệ trước giờ chiếu tạm tính, sức chứa chốt khi bắt đầu; không gọi doanh thu | Định nghĩa báo cáo và dữ liệu sức chứa suất |

Sau khi Kha duyệt hoặc điều chỉnh, cập nhật nhãn tương ứng trong hai tài liệu; tiếp theo mới chốt ERD/schema và hợp đồng API. Đạt nhận tài liệu đã duyệt và API khi tới giai đoạn frontend; không cần Đạt xác nhận để hoàn thành bản đầu này.

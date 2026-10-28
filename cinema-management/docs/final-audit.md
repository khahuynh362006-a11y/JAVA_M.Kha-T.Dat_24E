# Bàn giao và kiểm tra cuối đợt 28/10/2026

## Kết luận theo phạm vi

Backend đã kiểm thử88ca và27bướcHTTP trên JAR, không failure/error/skip. Chưa kiểm chứng qua giao diện của Đạt hoặc máy Đạt. Không đánh dấu toàn bộ đồ án hoàn thành. Static hiện26tệp0byte, được giữ nguyên; phần giao diện có thể nằm ngoài phạm vi truy cập, không suy luận Đạt chưa làm ở nơi khác.

## Chức năng thực sự có

CookieBCryptCSRF/auth; CRUDgenre/movie nhiều thể loại; room/physicalseats vàbulk; schedule snapshot/buffer15/concurrency/HATEOAS; map theo suất vàbooking/replay/admincancel; bốn thống kêJdbcTemplate. UserCRUD/statisticsrootdashboard tổngquan nângcao chưa có. STAFF không hủy, không xem statsquản trị. Không có thanh toán/email/đồ ăn/khuyến mãi/giữ ghế tạm.

## Kiểm tra và bằng chứng

- DB mới cinema_management_test_final_20261028_103848 trênLaragon8.4.3/3306. Baseline→seed→V001 theoREADME; Hibernatevalidate vàbuild đạt, startup không tựcreate/reset schema.
- Testsummary: Foundation12+MySQL5+Security14+Catalog13+RoomSeat12+Showtime13+Booking15+Statistics4=88.
- JAR đóng gói27HTTPsteps: auth/me/logout, CRUD vàvalidation, HATEOAS, trùng lịch/ghế, idempotency, cancel/rebook, permission403 vàJDBC260000VND trênfixture. Cổng thử riêng, đãdừng đúngprocess, không dừng app8080. Credentialdemo ngẫu nhiên bị vô hiệu hóa/xóa hash.
- Mười ca concurrency từbooking7vàshowtime3 dùng hai physicalconnections thật. Kiểm tra cuối không trùngactive/partial theoJSON; collection tuần tự không dùng thay bằngchứng.
- Statsfixture:2phiếu/3ghế/260000; hủy1phiếu/2ghế/180000;3/4=75%;3/2=150%+anomaly;0capacity/missinghistory→null. Ngàyhủy không phải ngàycreated/ngàychiếu. Fixture tổnghợp, không phải dữ liệu kinh doanh hoặc dự báo.
- ReportDOCX11trang đã render bằngWord bảncài sẵn vàPoppler, xem đủ11PNG, sửa trangtrắng/chúthích/styleTitle. RendererLibreOfficebundle không cóWindows nên dùngWord; không giảđịnh đãQA chỉ từXML.
- Postman151request vàenvironmentmẫu JSON/body hợp lệ, credentialtrống; chưa chạy trongứngdụngPostman. Cóassertions vàcase401/403/400/404/409 ởnhóm đã có.

Chứng cứ trong docs/reports/evidence: test-summary.json, packaged-api-evidence.json, statistics-evidence.json, haiPNG APIthật, concurrencyJSON, sourceJdbc vàdocument-qa.json. Không cótoken/cookie/mậtkhẩu trongartifact. QA PDF/PNG toànbộreport trongtarget chỉlàintermediate, không thaydeliverableWord.

## Lỗi và giới hạn

Lượt cuối không phát hiện lỗi backend mới sau các ca trên. Lỗi cũ đãsửa: projection association sauinsert, offsetDTO, bỏcontrollergiả khiAPIthật có. Hạnchế: capacity_snapshot quákhứ phầnlớnnull/chưa scheduler, chưa tải lớn hoặcEXPLAINtốiưu cầnindex, chưa máyĐạt/JDK21runtime/HTTPS. Không bịa mẫu sốlịch sử vàkhôngclamp100. BáocáoWord cònthôngtincầnnhóm bổsung họtên/MSSV/lớp/giảngviên; không bịađónggópĐạt.

## Việc Kha cần duyệt

D-01…D-12cònđềxuất: quyền đổi ca/tra cứu vàhủySTAFF, cửa sổhủy, giới hạn20ghế, tuổi/giáVIP, bảo vệADMINcuối/thu hồi phiên, chốt sức chứa vàđịnh nghĩa chỉsố. Stats hiện hủy theocancelled_at; đây là chỉ sốsựkiệnhủy riêng để phânbiệt ngàychiếu. Trước chạyDBthật, ràV001 vàchạy mộtlầnnếuchưacó, không nhập lại baseline/seed. Không tự ápV001 vàoDB sửdụngthực tế trongđợtthử.

## Việc Đạt cần nghiệm thu

Theo integration-checklist.md: UIauth/tokenrotation, nullablePUT/fullfields, phântrang0, thời gian+07/qua ngày, HATEOAS/roles, mapvật lýkhácmaptheosuất, UUIDretry/cancel/rebook, errorsfield vàstatsnull/tạmtính. Ghi expected/actual/requestresponsechebí mật. Không đổi hợpđồngđểchữaUI màchưađối chiếu.

## Tệp thay đổi ở bước cuối

Java: controller/StatisticsController.java; service/StatisticsService.java; repository/jdbc/StatisticsJdbcRepository.java; dto/statistics/StatisticsResponse.java vàStatisticsRows.java; config/SecurityConfig.java thêm4GETADMIN; test/integration/StatisticsMySqlIT.java. Không sửaCRUD/JPA/schema/migration/pom/static.

Tài liệu: README.md, docs/api.md, statistics.md, test-cases.md, integration-checklist.md, demo.md, final-audit.md; Postmancollection/environment; reports/Bao_cao_do_an_rap_phim_Kha_Dat.docx, evidence vàscripts/build_report.py/capture_api_evidence.cjs. Cáchelper report khôngphải dependencybackend.

Không có Gitrepository/nhánh nênkhôngcógitdiffđểđối chiếu; đã kiểm tra đầu/cuối, khônginit/commit/push/merge. Hướng dẫnkhởi động/demo ởREADME vàdemo.md; không phụthuộcđường dẫnKha.

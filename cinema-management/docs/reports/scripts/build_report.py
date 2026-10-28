from pathlib import Path
import json,math,hashlib
from PIL import Image,ImageDraw,ImageFont
from docx import Document
from docx.shared import Cm,Pt,RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.enum.table import WD_TABLE_ALIGNMENT,WD_CELL_VERTICAL_ALIGNMENT
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
root=Path('docs/reports');ev=root/'evidence';ev.mkdir(parents=True,exist_ok=True)
# A condensed ERD is a source-derived diagram, not a fabricated runtime screenshot.
im=Image.new('RGB',(1640,1460),'white');d=ImageDraw.Draw(im)
font=ImageFont.truetype('C:/Windows/Fonts/arial.ttf',32);bold=ImageFont.truetype('C:/Windows/Fonts/arialbd.ttf',40)
nodes={'users':(30,40,['id PK']),'roles':(430,40,['id PK','code UK']),'user_roles':(230,390,['user_id FK','role_id FK']),
'movies':(830,40,['id PK','code UK']),'genres':(1230,40,['id PK','name UK']),'movie_genres':(1030,390,['movie_id FK','genre_id FK']),
'rooms':(30,740,['id PK','code UK']),'seats':(430,740,['id PK','room_id FK']),
'showtimes':(830,740,['id PK','movie_id FK','room_id FK']),'bookings':(1230,740,['id PK','showtime_id FK','request key hash']),
'booking_seats':(1230,1100,['id PK','booking_id FK','seat_id FK'])}
def line(points):
 d.line(points,fill='#7b8794',width=4);x,y=points[-1];px,py=points[-2];ang=math.atan2(y-py,x-px);p1=(x-18*math.cos(ang-.4),y-18*math.sin(ang-.4));p2=(x-18*math.cos(ang+.4),y-18*math.sin(ang+.4));d.polygon([(x,y),p1,p2],fill='#7b8794')
for points in [[(205,290),(205,340),(405,340),(405,390)],[(605,290),(605,340),(405,340),(405,390)],[(1005,290),(1005,340),(1205,340),(1205,390)],[(1405,290),(1405,340),(1205,340),(1205,390)],[(1005,290),(800,290),(800,690),(1005,690),(1005,740)],[(205,740),(205,690),(1005,690),(1005,740)],[(380,865),(430,865)],[(1180,865),(1230,865)],[(1405,990),(1405,1100)],[(605,990),(605,1405),(1405,1405),(1405,1350)],[(30,165),(8,165),(8,1040),(1250,1040),(1250,990)]]:line(points)
for name,(x,y,fields) in nodes.items():
 d.rounded_rectangle((x,y,x+350,y+250),radius=10,fill='#f6f8fa',outline='#425466',width=3);d.text((x+18,y+17),name,font=bold,fill='#132538');d.line((x,y+80,x+350,y+80),fill='#9ca8b5',width=2)
 for i,f in enumerate(fields):d.text((x+18,y+96+i*43),f,font=font,fill='#18212b')
im.save(ev/'erd-current-overview.png')
summary=json.loads((ev/'test-summary.json').read_text(encoding='utf-8'));stats=json.loads((ev/'statistics-evidence.json').read_text(encoding='utf-8'));api=json.loads((ev/'packaged-api-evidence.json').read_text(encoding='utf-8'));bk=json.loads((ev/'booking-concurrency.json').read_text(encoding='utf-8'));sh=json.loads((ev/'showtime-concurrency.json').read_text(encoding='utf-8'))
doc=Document();sec=doc.sections[0];sec.top_margin=Cm(2.1);sec.bottom_margin=Cm(2);sec.left_margin=Cm(2.4);sec.right_margin=Cm(2.1)
for style in ['Normal','Title','Subtitle','Heading 1','Heading 2']:
 s=doc.styles[style];s.font.name='Times New Roman';s.font.color.rgb=RGBColor(0,0,0);s.element.get_or_add_rPr().rFonts.set(qn('w:eastAsia'),'Times New Roman')
doc.styles['Normal'].font.size=Pt(12);doc.styles['Normal'].paragraph_format.line_spacing=1.12;doc.styles['Normal'].paragraph_format.space_after=Pt(7)
doc.styles['Title'].font.size=Pt(23);doc.styles['Heading 1'].font.size=Pt(16);doc.styles['Heading 2'].font.size=Pt(13)
sec.different_first_page_header_footer=True
p=sec.footer.paragraphs[0];p.alignment=WD_ALIGN_PARAGRAPH.CENTER;r=p.add_run();f=OxmlElement('w:fldSimple');f.set(qn('w:instr'),'PAGE');r._r.addnext(f)
def title(t):doc.add_paragraph(t,'Heading 1')
def para(t):return doc.add_paragraph(t)
def page(t):
 p=doc.add_paragraph(t,'Heading 1');p.paragraph_format.page_break_before=True
def table(headers,rows,widths):
 t=doc.add_table(rows=1,cols=len(headers));t.alignment=WD_TABLE_ALIGNMENT.CENTER;t.autofit=False
 for i,w in enumerate(widths):t.columns[i].width=Cm(w)
 for i,h in enumerate(headers):t.rows[0].cells[i].text=str(h)
 for row in rows:
  cells=t.add_row().cells
  for i,value in enumerate(row):cells[i].text=str(value)
 for ri,row in enumerate(t.rows):
  trpr=row._tr.get_or_add_trPr();cant=OxmlElement('w:cantSplit');trpr.append(cant)
  if ri==0:repeat=OxmlElement('w:tblHeader');trpr.append(repeat)
  for ci,c in enumerate(row.cells):
   c.width=Cm(widths[ci]);c.vertical_alignment=WD_CELL_VERTICAL_ALIGNMENT.CENTER;pr=c._tc.get_or_add_tcPr();m=OxmlElement('w:tcMar')
   for side in ['top','left','bottom','right']:
    e=OxmlElement('w:'+side);e.set(qn('w:w'),'85');e.set(qn('w:type'),'dxa');m.append(e)
   pr.append(m);borders=OxmlElement('w:tcBorders')
   for side in ['top','left','bottom','right']:
    e=OxmlElement('w:'+side);e.set(qn('w:val'),'single');e.set(qn('w:sz'),'4');e.set(qn('w:color'),'D9D9D9');borders.append(e)
   pr.append(borders);shade=OxmlElement('w:shd');shade.set(qn('w:fill'),'233B53' if ri==0 else ('F1F4F7' if ri%2==0 else 'FFFFFF'));pr.append(shade)
   for p in c.paragraphs:
    p.paragraph_format.space_after=Pt(3);p.paragraph_format.space_before=Pt(3);p.paragraph_format.line_spacing=1.05
    if ci==1 and len(str(c.text))<16:p.alignment=WD_ALIGN_PARAGRAPH.CENTER
    for r in p.runs:r.font.name='Times New Roman';r.font.size=Pt(10.5);r.font.color.rgb=RGBColor(255,255,255) if ri==0 else RGBColor(0,0,0);r.bold=ri==0
 doc.add_paragraph().paragraph_format.space_after=Pt(0)
 return t
p=doc.add_paragraph('BÁO CÁO ĐỒ ÁN','Subtitle');p.alignment=1
p=doc.add_paragraph('Xây dựng hệ thống quản lý rạp chiếu phim bằng Java Spring Boot','Title');p.alignment=1
p=doc.add_paragraph('Backend và bàn giao tích hợp giao diện','Subtitle');p.alignment=1
para('Môn học Lập trình Java  |  Ngày 28 tháng 10 năm 2026')
para('Hệ thống quản lý một rạp có nhiều phòng, phục vụ ADMIN và STAFF tại quầy. Bản báo cáo trình bày phạm vi backend đã thực hiện, kết quả kiểm thử và phần cần nghiệm thu tiếp với giao diện.')
para('Backend đạt 88 kiểm thử và 27 bước HTTP trên JAR đóng gói. Chưa kiểm chứng qua giao diện của Đạt hoặc trên máy Đạt. Các tệp frontend trong project truy cập hiện còn rỗng; không coi backend build thành công là toàn bộ đồ án hoàn tất.')
table(['Thành viên','Phạm vi theo phân công'],[['Kha','Backend CSDL API và kiểm thử'],['Đạt','Frontend và tích hợp theo API bàn giao']],[3,13])
para('Thông tin nhóm cần bổ sung: họ và tên đầy đủ, MSSV của Kha và Đạt, lớp, tên giảng viên, đơn vị đào tạo. Báo cáo không suy luận các thông tin này từ tên thư mục hoặc tài khoản máy.')
para('Nguồn: mã nguồn và SQL hiện tại, tài liệu nghiệp vụ CSDL API, kết quả JUnit và HTTP thực tế trong docs/reports/evidence. Dữ liệu minh họa chỉ dùng kiểm thử, không phải số liệu kinh doanh rạp thật.')
page('1 Mục tiêu nghiệp vụ và phân công')
para('MVP gồm danh mục phim và thể loại, phòng và ghế vật lý, lịch chiếu, đặt chỗ tại quầy, hủy giữ lịch sử và thống kê quản trị. Không triển khai khách đặt online, thanh toán, hoàn tiền, đồ ăn, khuyến mãi hoặc gửi email SMS.')
table(['Chức năng','ADMIN','STAFF'],[['Đăng nhập xem tài khoản đăng xuất','Có','Có'],['CRUD phim thể loại phòng ghế','Có','Không'],['Quản lý lịch chiếu','Có','Chỉ đọc'],['Đặt và tra cứu tại quầy','Có','Có'],['Hủy đặt chỗ trước giờ chiếu','Có','Chờ duyệt'],['Thống kê quản trị','Có','Không']],[8,4,4])
para('Kha phụ trách backend CSDL API theo yêu cầu. Đạt phụ trách frontend theo phân công; chưa có bằng chứng giao diện hoàn chỉnh trong project này. Tài liệu tích hợp ghi các trường JSON, lỗi theo ô nhập, phân trang và trạng thái để Đạt xây dựng sau.')
para('Các lựa chọn D 01 đến D 12 vẫn là đề xuất chờ Kha duyệt. Hàng rào bảo vệ đang áp dụng gồm khóa cấu trúc ghế khi phòng đã có suất, khóa phim theo lịch sử suất, chỉ hủy booking mới trước giờ bắt đầu và chưa cấp hủy cho STAFF.')
para('Phim phòng ghế dùng ACTIVE INACTIVE. Suất lưu SCHEDULED CANCELLED; đang chiếu và hoàn thành được suy ra theo đồng hồ server. Booking lưu CONFIRMED CANCELLED, không xóa để hủy và không khôi phục booking đã hủy.')
page('2 Kiến trúc và cấu trúc dự án')
para('Spring Boot tổ chức controller → service → repository. Controller nhận DTO và HTTP; service kiểm tra nghiệp vụ cùng transaction; repository JPA phục vụ CRUD và repository JDBC phục vụ thống kê. Mapper trả DTO, không serialize entity hoặc association vòng lặp.')
table(['Thành phần','Vai trò'],[['controller dto mapper','Hợp đồng HTTP và dữ liệu ra vào'],['service exception','Nghiệp vụ transaction và lỗi an toàn'],['repository entity','JPA Hibernate khóa và mapping schema'],['repository jdbc','JdbcTemplate và SQL thống kê có tham số'],['config','Security cookie CSRF Clock và lỗi filter'],['database docs src test','DDL migration hướng dẫn và kiểm thử']],[5,11])
para('Bộ công cụ thực tế: Spring Boot 3.5.16, Maven Wrapper 3.9.16, Java target 21 và JDK chạy thử 25.0.4.1, MySQL Laragon 8.4.3. Chưa kiểm chứng runtime JDK 21 hoặc máy Đạt. Phiên bản được giữ trong pom và README; không thêm dependency thống kê vì JdbcTemplate đã có qua nền tảng JDBC hiện tại.')
para('Cookie JSESSIONID có HttpOnly và SameSite Lax; Secure bật khi dùng HTTPS. Đăng nhập JSON dùng BCrypt, thay session ID; CSRF được giữ cho login logout và thao tác ghi. Trình duyệt lấy token mới sau login. Không dùng JWT hoặc CORS mở rộng.')
para('GlobalExceptionHandler xử lý lỗi MVC; Security dùng entry point và access denied handler riêng để cùng trả timestamp status code message path fieldErrors. Không trả stack trace SQL mật khẩu hoặc hash.')
page('3 Cơ sở dữ liệu và sơ đồ quan hệ')
doc.add_picture(str(ev/'erd-current-overview.png'),width=Cm(16))
para('Hình 1  ERD rút gọn các quan hệ chính từ schema và V001. Chi tiết toàn bộ cột khóa ghép và khóa audit nằm trong database/schema.sql cùng docs/database-erd.mmd. Sơ đồ không phải ảnh kết quả kiểm thử.')
para('11 bảng InnoDB utf8mb4 gồm users roles user_roles movies genres movie_genres rooms seats showtimes bookings booking_seats. Tất cả FK dùng RESTRICT. Mỗi suất chụp thời lượng và tuổi; booking chụp tên phòng phim nhân viên cùng mã loại giá ghế để lịch sử không phụ thuộc danh mục hiện tại.')
para('V001 thêm key và hash idempotency nullable cho dữ liệu cũ, UNIQUE theo người tạo và key, CHECK cặp metadata cùng trigger bất biến. Sau nâng cấp có 8 trigger. Không sửa baseline đã áp dụng, không backfill lịch sử hoặc tự chạy DDL khi startup.')
page('4 REST JPA JDBC và HATEOAS')
table(['Nhóm API','Phạm vi thực tế'],[['api auth','CSRF login me logout bằng cookie'],['api admin genres movies','CRUD tìm kiếm phân trang quan hệ nhiều thể loại'],['api admin rooms và seats lồng','CRUD phòng ghế bulk không reset'],['api admin showtimes','CRUD có điều kiện hủy và detail HATEOAS'],['api showtimes','STAFF và ADMIN đọc lịch cùng sơ đồ theo suất'],['api bookings','Tạo replay tra cứu và ADMIN hủy'],['api admin statistics','Bốn báo cáo đọc bằng JdbcTemplate']],[5,11])
para('JPA map tiền bằng BigDecimal, thời gian theo giờ Việt Nam, cột generated không ghi trực tiếp. ddl auto là validate, sql init là never, open in view tắt. Không cascade REMOVE lịch sử. Các bảng nối có entity và khóa ghép; trang phim được tải trước rồi lấy thể loại theo lô để tránh N cộng 1 hoặc lặp phim.')
para('Detail suất dùng Spring HATEOAS EntityModel với trường DTO ở root và _links. ADMIN nhận self collection movie room; update delete cancel chỉ xuất hiện nếu chưa bắt đầu và chưa có booking. STAFF chỉ nhận liên kết đọc được theo quyền. Các thao tác vẫn phải kiểm tra lại điều kiện khi ghi.')
para('Minh chứng JAR cho thấy HTTP 201 tạo tài nguyên, 200 đọc sửa replay, 204 xóa hợp lệ, 400 validation, 401 thiếu phiên, 403 sai quyền hoặc CSRF, 409 xung đột. HATEOAS được gọi thực tế trong suite và JAR. Xem packaged-api-evidence.json và api-hateoas.png; đây là API JSON, không phải giao diện Đạt.')
para('JdbcTemplate giữ SQL ở StatisticsJdbcRepository. Giá trị ngày phòng phim size offset đều bind parameter; tên SQL và thứ tự sắp xếp cố định. Aggregate phiếu và ghế tách riêng, không JOIN nhiều thể loại làm nhân số lượng hoặc tổng tiền.')
page('5 Giao dịch và bảo vệ dữ liệu')
para('Lịch chiếu ghi READ COMMITTED, khóa phim nguồn đích rồi phòng nguồn đích theo ID tăng, sau đó khóa suất và xác minh context. SELECT xung đột chạy sau khi lấy khóa phòng nên thấy dữ liệu request trước đã commit, kể cả phòng đang trống. Không dùng synchronized làm bảo vệ duy nhất.')
para('Hai suất cùng phòng xung đột khi newStart < existingEnd + 15 phút và existingStart < newEnd + 15 phút. Đúng 15 phút hợp lệ; qua nửa đêm không chỉ so ngày hoặc giờ bắt đầu. Sửa loại chính id của nó, thời lượng và giờ kết thúc được lưu ổn định.')
para('Đặt chỗ dùng khóa phim → phòng → suất → booking khi hủy → ghế ID tăng. UNIQUE theo showtime và active seat do generated column quản lý ngăn hai booking CONFIRMED cùng ghế. Hủy update đúng cha, trigger đổi chi tiết theo booking id rồi giải phóng slot, không cập nhật lần đặt mới.')
table(['Ca đồng thời','HTTP thực tế','Đối chiếu cuối'],[[x['case'],str(x['statuses']),'Trùng ghế 0 và phiếu thiếu 0'] for x in bk],[6,3,7])
para('Các ca dùng hai HttpClient phiên riêng và hai CONNECTION ID thật, được giữ đồng thời bằng barrier chỉ trong mã test. Lịch có thêm ba ca create edit transfer đều không còn cặp giao khoảng. Collection chạy tuần tự không được dùng thay bằng chứng này.')
page('6 Idempotency và giá lịch sử')
para('Frontend tạo UUID một lần cho một ý định đặt và gửi Idempotency Key cùng body. Cùng actor key và nội dung canonical trả booking đã tạo; cùng key khác nội dung trả 409. Đảo thứ tự ID ghế không đổi ý định; ID trùng bị từ chối. Sau timeout không tự sinh key mới.')
para('UNIQUE theo created by và key lưu ở DB nên không phụ thuộc cache một JVM. SHA 256 canonical gồm suất danh sách ghế tăng price version và age checked. Khi va chạm UNIQUE, transaction thua rollback trước rồi facade mới đọc kết quả đã commit; không tiếp tục transaction đã hỏng.')
para('Replay trả trạng thái hiện tại của booking gốc, kể cả CANCELLED, không đặt lại ghế. Đặt lại sau hủy dùng key mới. Hủy lặp giữ nguyên audit và không ảnh hưởng booking thay thế. STAFF hiện bị chặn hủy kể cả đơn mình tạo.')
para('Người tạo lấy từ session; giá từ DB. STANDARD dùng giá thường, VIP dùng giá VIP, không hệ số hoặc phụ phí mới. Tổng cộng BigDecimal VND nguyên, không cần làm tròn khi đặt. Snapshot giữ từng đơn giá và tổng. Không có thanh toán hoặc hoàn tiền giả lập.')
para('JAR đóng gói đã chạy 27 bước HTTP gồm tạo genre phim phòng ghế suất, thử validation và trùng lịch, tạo booking và replay cùng id, trùng ghế 409, hủy rồi đặt lại, hủy lặp giữ booking mới, STAFF bị 403 và logout làm me trả 401. Toàn bộ response an toàn được lưu trong packaged-api-evidence.json, không có cookie token hoặc mật khẩu.')
page('7 Định nghĩa thống kê quản trị')
table(['Chỉ số','Đơn vị và trạng thái','Thời gian'],[['Suất theo ngày','Tổng SCHEDULED và CANCELLED','starts at'],['Ghế theo phim','Phiếu và ghế CONFIRMED thuộc suất SCHEDULED','starts at'],['Tỷ lệ lấp đầy','Ghế hiệu lực chia mẫu số hợp lệ nhân 100','starts at'],['Đặt chỗ hủy','Phiếu và ghế CANCELLED cùng giá trị danh nghĩa','cancelled at']],[4.2,7.5,4.3])
para('from to bao gồm cả hai ngày, SQL dùng từ đầu ngày đến trước đầu ngày kế tiếp, tối đa 31 ngày. Múi giờ Asia Ho Chi Minh tương ứng +07. Ngày chiếu khác ngày tạo booking và ngày hủy. Ngày không dữ liệu trả 0 cho bảng theo ngày; bảng phim suất trả rỗng. Giá trị booking không phải doanh thu thực thu, giá trị hủy không phải hoàn tiền.')
para('Mẫu số ưu tiên capacity snapshot. Nếu chưa bắt đầu và snapshot null, dùng số ghế ACTIVE hiện tại với nhãn tạm tính. Nếu đã bắt đầu mà snapshot null, tỷ lệ và mẫu số trả null vì không có dữ liệu lịch sử. Không lấy sơ đồ hiện tại để suy ra quá khứ hoặc tự backfill snapshot.')
para('Mẫu số 0 không chia, trả null. Tỷ lệ có mẫu số hợp lệ làm tròn hai chữ số HALF UP, có thể vượt 100 và kèm anomaly true, không cắt để che dữ liệu sai. Hiện chưa có scheduler chốt sức chứa; chính sách này cần Kha duyệt trước khi thống kê lịch sử đầy đủ.')
para('Chỉ ADMIN đọc thống kê. Response có from to timeZone timeBasis asOf items total page size totalPages; không trả dữ liệu cá nhân. Chưa benchmark tải lớn; chưa có lý do đo được để thêm chỉ mục hoặc migration mới ở bước này.')
page('8 Đối chiếu JDBC với dữ liệu tính tay')
table(['Fixture kiểm thử','Mong đợi','Thực tế'],[['Phim A có nhiều thể loại','2 phiếu 3 ghế 260000','Khớp'],['Một phiếu hủy hai ghế vào 00 giờ','1 phiếu 2 ghế 180000','Khớp'],['Ba ghế trên snapshot bốn','75 phần trăm','75.00'],['Snapshot hai nhưng có ba ghế','150 và anomaly','Khớp'],['Quá khứ thiếu snapshot hoặc mẫu số 0','Tỷ lệ null','Khớp']],[8,5,3])
para('Fixture SQL cố định theo ngày D trong statistics-evidence.json, bao gồm dữ liệu legacy hoặc bất thường ngoài điều kiện API để kiểm tra phép đếm. Đây là dữ liệu tổng hợp kiểm thử, không phải kết quả kinh doanh hay dự báo rạp. Sự kiện 23 giờ 59 phút 59 giây ngày trước không vào ngày D; suất qua nửa đêm thuộc ngày bắt đầu.')
shape=doc.add_picture(str(ev/'api-statistics.png'),width=Cm(14))
doc.paragraphs[-1].alignment=WD_ALIGN_PARAGRAPH.CENTER
doc.paragraphs[-1].paragraph_format.keep_with_next=True
# Word crop removes blank screenshot margins only; actual response values are unchanged.
blip=shape._inline.graphic.graphicData.pic.blipFill;crop=OxmlElement('a:srcRect');crop.set('r','45000');crop.set('b','48000');blip.insert(1,crop);shape.height=int(shape.height*0.52/0.55)
para('Hình 2  JSON thống kê thực tế từ JAR trong Chrome headless. Cắt khoảng trắng khi đặt vào Word, không thay dữ liệu. API trả hai phiếu ba ghế và 260000 VND. Không phải ảnh frontend Đạt.')
page('9 Kiểm thử và mức độ hoàn thành')
table(['Suite','Số ca','Kết quả'],[[r['name'].rsplit('.',1)[-1],r['tests'],'Đạt'] for r in summary['suites']],[10,2.5,3.5])
para('Tổng 88 ca đều đạt, không failure error hoặc skip. DB mới cinema_management_test_final_20261028_103848 được ghi trong test-summary.json; nhập từ trạng thái trống đúng baseline seed V001, Hibernate validate thành công. Tài khoản thử vô hiệu hóa và xóa hash sau chạy, lịch sử fixture được giữ theo trigger. Không DROP hoặc reset DB đang sử dụng.')
para('JAR thực sự chạy ở cổng riêng và được dừng sau demo, không dừng ứng dụng khác trên cổng 8080. Minh chứng gồm JSON API, hai ảnh JSON thật, kết quả JUnit đã lọc metadata, SQL JDBC và JSON concurrency. Không sao chép cookie CSRF hoặc mật khẩu vào báo cáo.')
para('Các lỗi backend đã xử lý ở các đợt trước gồm association chỉ đọc sau insert chuyển sang projection, giữ offset gốc ở DTO lịch và bỏ controller giả khi endpoint thật đã có. Lượt cuối không phát hiện lỗi mới sau các ca kiểm tra này. Không diễn giải điều đó là bảo đảm mọi lỗi hoặc tải lớn đều đã được kiểm chứng.')
para('Frontend gồm 26 tệp rỗng trong phạm vi truy cập. Chưa kiểm chứng qua giao diện của Đạt, chưa chạy trên máy Đạt hoặc runtime JDK 21. Postman có assertion và environment mẫu nhưng chưa chạy trực tiếp trong ứng dụng Postman. Các điểm này là phần nghiệm thu còn lại, không phải backend đã hoàn thành toàn bộ dự án.')
page('10 Cài đặt demo và kế hoạch bàn giao')
para('Cài JDK và MySQL tương thích, đặt JAVA HOME và DB HOST PORT NAME USERNAME PASSWORD bằng môi trường của máy chạy. Từ thư mục pom, nhập schema.sql rồi seed.sql rồi V001 trên DB mới; DB có baseline chỉ chạy migration còn thiếu một lần sau đối chiếu. Không lưu mật khẩu hoặc đường dẫn tài khoản Kha vào cấu hình chia sẻ.')
para('Build bằng .\\mvnw.cmd package, chạy java -jar target/cinema-management-0.0.1-SNAPSHOT.jar theo README. ddl auto validate và sql init never không tự tạo lại DB. Dùng local accounts profile chủ động để cấp ADMIN STAFF riêng rồi bỏ profile khi chạy web. Chưa áp dụng V001 vào DB sử dụng thực tế của Kha ở các lượt thử.')
para('Demo theo thứ tự đăng nhập → genre phim → phòng và ghế → lịch và HATEOAS → đặt nhiều ghế → replay → hủy và đặt lại → JDBC. Thử trùng lịch ghế sai quyền và validation. Chọn dữ liệu minh họa riêng, không reset seed, không giả lập thu tiền. Hướng dẫn chi tiết ở docs/demo.md và docs/api.md.')
para('Đạt cần chạy integration checklist trên giao diện và máy mình: cookie CSRF, trường nullable, PUT đầy đủ, phân trang từ 0, thời gian +07, phân biệt ghế vật lý với ghế theo suất, retry cùng idempotency key và null tỷ lệ. Với lỗi UI, lưu bước tái hiện request response đã che bí mật; chỉ sửa backend khi lỗi thuộc backend.')
para('Kha cần duyệt các chính sách D 01 đến D 12, nhất là quyền tra cứu đổi ca và hủy STAFF, thời hạn hủy, giới hạn 20 ghế, tuổi nội bộ, hai giá tường minh, bảo vệ ADMIN cuối cùng và chốt sức chứa. Nhóm cần bổ sung họ tên MSSV lớp giảng viên trước nộp. Không khẳng định đóng góp frontend chưa có bằng chứng.')
para('Hướng phát triển sau nghiệm thu gồm hoàn thiện giao diện, quản trị tài khoản có thu hồi phiên, chốt sức chứa có lịch sử và đo hiệu năng để tối ưu truy vấn. Thanh toán và khách tự đặt chỉ thực hiện khi có phạm vi mới. Chưa commit push hoặc merge; workspace chưa có Git repository để đối chiếu nhánh.')
# Remove template title border residue and theme font overrides before saving.
for tree in [doc.element,doc.styles.element]:
    for border in list(tree.xpath('.//w:pBdr')):
        border.getparent().remove(border)
    for fonts in tree.xpath('.//w:rFonts'):
        for name in ['asciiTheme','hAnsiTheme','cstheme','eastAsiaTheme']:
            fonts.attrib.pop(qn('w:'+name),None)
        fonts.set(qn('w:ascii'),'Times New Roman');fonts.set(qn('w:hAnsi'),'Times New Roman')
doc.core_properties.title='Xây dựng hệ thống quản lý rạp chiếu phim bằng Java Spring Boot';doc.core_properties.author='Kha và Đạt';doc.core_properties.subject='Báo cáo backend và bàn giao tích hợp';doc.core_properties.comments=''
output=root/'Bao_cao_do_an_rap_phim_Kha_Dat.docx';doc.save(output);print('DOCX saved')

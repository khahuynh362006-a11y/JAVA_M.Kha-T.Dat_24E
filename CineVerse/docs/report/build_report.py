from pathlib import Path
from docx import Document
from docx.shared import Cm, Pt, RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from PIL import Image, ImageDraw, ImageFont
import json, re

ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'docs/report'
SHOTS=OUT/'screenshots'
doc=Document()
s=doc.sections[0]
s.page_width=Cm(21);s.page_height=Cm(29.7)
s.top_margin=s.bottom_margin=Cm(2);s.left_margin=Cm(2.5);s.right_margin=Cm(2)
s.different_first_page_header_footer=True
for name in ['Normal','Title','Subtitle','Heading 1','Heading 2','Heading 3','Caption']:
 st=doc.styles[name];st.font.name='Times New Roman';st.font.color.rgb=RGBColor(0,0,0)
 st.element.get_or_add_rPr().append(OxmlElement('w:rFonts'))
 st.element.rPr.rFonts.set(qn('w:eastAsia'),'Times New Roman')
doc.styles['Normal'].font.size=Pt(13)
pf=doc.styles['Normal'].paragraph_format
pf.line_spacing=1.15;pf.space_after=Pt(6);pf.alignment=WD_ALIGN_PARAGRAPH.JUSTIFY
for name,size in [('Title',22),('Heading 1',16),('Heading 2',14),('Heading 3',13)]:
 doc.styles[name].font.size=Pt(size);doc.styles[name].font.bold=True
doc.styles['Heading 1'].paragraph_format.page_break_before=True
doc.styles['Caption'].font.size=Pt(12)
f=s.footer.paragraphs[0];f.alignment=WD_ALIGN_PARAGRAPH.CENTER
f.add_run('CineVerse  |  ')
field=OxmlElement('w:fldSimple');field.set(qn('w:instr'),'PAGE');f._p.append(field)
head=s.header.paragraphs[0];head.text='BÁO CÁO ĐỒ ÁN MÔN LẬP TRÌNH JAVA';head.style='Caption'

def p(t): return doc.add_paragraph(t)
def h(t,l=2): return doc.add_heading(t,l)
table_no=0
table_titles=['Yêu cầu và phạm vi chức năng','Công nghệ sử dụng','Chức năng theo tác nhân','Đầu vào xử lý và đầu ra nghiệp vụ','Thiết kế dữ liệu cốt lõi','Các endpoint REST API tiêu biểu','Cấu trúc mã nguồn','Các trường hợp kiểm thử','Phân công nhóm cần xác nhận']
def table(headers,rows):
 global table_no
 table_no+=1
 caption=doc.add_paragraph(f'Bảng {table_no}. {table_titles[table_no-1]}',style='Caption');caption.paragraph_format.keep_with_next=True
 t=doc.add_table(rows=1,cols=len(headers));t.style='Table Grid'
 for cell,text in zip(t.rows[0].cells,headers):
  cell.text=text
  cell.paragraphs[0].paragraph_format.keep_with_next=True
  for r in cell.paragraphs[0].runs:r.bold=True
  shade=OxmlElement('w:shd');shade.set(qn('w:fill'),'EAEAEA');cell._tc.get_or_add_tcPr().append(shade)
 tr=OxmlElement('w:tblHeader');t.rows[0]._tr.get_or_add_trPr().append(tr)
 for row in rows:
  for c,text in zip(t.add_row().cells,row):
   c.text=str(text)
   for para in c.paragraphs:para.paragraph_format.space_after=Pt(4);para.paragraph_format.line_spacing=1.05;para.alignment=WD_ALIGN_PARAGRAPH.LEFT
 for row in t.rows:
  pr=row._tr.get_or_add_trPr();pr.append(OxmlElement('w:cantSplit'))
 doc.add_paragraph()
 return t
fig_no=0
def fig(file,title,explain,width=16.5):
 global fig_no
 path=SHOTS/file
 if not path.exists():return
 fig_no+=1
 para=doc.add_paragraph();para.alignment=WD_ALIGN_PARAGRAPH.CENTER;para.paragraph_format.keep_with_next=True
 para.add_run().add_picture(str(path),width=Cm(width if file.startswith('d-') else 15))
 cap=doc.add_paragraph(f'Hình {fig_no}. {title}',style='Caption');cap.alignment=WD_ALIGN_PARAGRAPH.CENTER;cap.paragraph_format.keep_with_next=True
 p(explain)

def diagram(file,nodes,edges,size=(1500,900)):
 im=Image.new('RGB',size,'white');d=ImageDraw.Draw(im)
 font=ImageFont.truetype('C:/Windows/Fonts/arial.ttf',28)
 for a,b,label in edges:
  x1,y1,x2,y2=nodes[a][0];u1,v1,u2,v2=nodes[b][0]
  start=((x1+x2)//2,y2);end=((u1+u2)//2,v1)
  if v1<y2:start=(x2,(y1+y2)//2);end=(u1,(v1+v2)//2)
  d.line([start,end],fill='black',width=3)
  import math
  angle=math.atan2(end[1]-start[1],end[0]-start[0]);a1=(end[0]-16*math.cos(angle-.4),end[1]-16*math.sin(angle-.4));a2=(end[0]-16*math.cos(angle+.4),end[1]-16*math.sin(angle+.4))
  if file!='d-usecase.png':d.polygon([end,a1,a2],fill='black')
  if label:d.text(((start[0]+end[0])//2+8,(start[1]+end[1])//2),label,font=font,fill='black')
 for key,(box,text) in nodes.items():
  if file=='d-usecase.png' and key in ['guest','user','admin']:
   cx=(box[0]+box[2])//2;top=box[1]
   d.ellipse((cx-20,top,cx+20,top+40),outline='black',width=3);d.line([(cx,top+40),(cx,top+95)],fill='black',width=3);d.line([(cx-45,top+60),(cx+45,top+60)],fill='black',width=3);d.line([(cx-35,top+130),(cx,top+95),(cx+35,top+130)],fill='black',width=3)
   label=text.split('\n')[0];tw=d.textbbox((0,0),label,font=font)[2];d.text((cx-tw//2,top+140),label,font=font,fill='black');continue
  if file=='d-usecase.png':d.ellipse(box,fill='#f5f5f5',outline='black',width=3)
  else:d.rounded_rectangle(box,radius=12,fill='#f5f5f5',outline='black',width=3)
  lines=text.split('\n');cy=(box[1]+box[3])//2-len(lines)*18
  for line in lines:
   w=d.textbbox((0,0),line,font=font)[2];d.text(((box[0]+box[2]-w)//2,cy),line,font=font,fill='black');cy+=36
 im.save(SHOTS/file)

# Academic cover follows the supplied course requirements.
for text,size,bold in [('TRƯỜNG CAO ĐẲNG CÔNG THƯƠNG TP.HCM',16,True),('LỚP CCQ2411E',14,True),('',13,False),('BÁO CÁO ĐỒ ÁN',22,True),('MÔN LẬP TRÌNH JAVA',18,True)]:
 para=doc.add_paragraph();para.alignment=WD_ALIGN_PARAGRAPH.CENTER
 r=para.add_run(text);r.font.size=Pt(size);r.bold=bold
doc.add_paragraph()
title=doc.add_paragraph('XÂY DỰNG WEBSITE BÁN VÉ XEM PHIM CINEVERSE',style='Title');title.alignment=WD_ALIGN_PARAGRAPH.CENTER
doc.add_paragraph()
for text in ['Giảng viên hướng dẫn: Nguyễn Minh Tuấn','Sinh viên thực hiện:','Lê Thành Đạt  –  MSSV 2124110145','Huỳnh Minh Kha  –  MSSV 2124110175','Khoa: ........................................................','Nhóm số: ....................................................']:
 para=p(text);para.alignment=WD_ALIGN_PARAGRAPH.CENTER
doc.add_paragraph();p('TP. Hồ Chí Minh, tháng 11 năm 2026').alignment=WD_ALIGN_PARAGRAPH.CENTER
h('NHẬN XÉT CỦA GIẢNG VIÊN',1)
p('Đề tài: Xây dựng website bán vé xem phim CineVerse')
p('Sinh viên: Lê Thành Đạt – 2124110145; Huỳnh Minh Kha – 2124110175.')
for i in range(12):p('........................................................................................................')
p('Điểm: ..............................             Ngày: ................................')
p('Giảng viên nhận xét và ký tên').alignment=WD_ALIGN_PARAGRAPH.RIGHT
h('MỤC LỤC',1)
fld=OxmlElement('w:fldSimple');fld.set(qn('w:instr'),'TOC \\o "1-2" \\h \\z \\u');doc.add_paragraph()._p.append(fld)
h('CHƯƠNG 1  GIỚI THIỆU ĐỀ TÀI',1)
h('1.1 Lý do chọn đề tài')
p('Đặt vé tại rạp đòi hỏi khách hàng biết phim, suất chiếu, giá và tình trạng ghế tại cùng một thời điểm. Khi hai người cùng chọn một ghế hoặc thời hạn giữ ghế đã kết thúc, hệ thống phải quyết định ở phía máy chủ để tránh phát hành vé trùng. Đây là bài toán phù hợp để vận dụng Java, giao dịch cơ sở dữ liệu và REST API trong một ứng dụng có nghiệp vụ rõ ràng.')
p('CineVerse được xây dựng cho hai nhóm người sử dụng: khách mua vé và quản trị viên vận hành rạp. Nhóm tập trung vào tính nhất quán của quy trình chọn phim, giữ ghế, xác nhận thanh toán và phát hành vé; giao diện là phương tiện thể hiện các trạng thái đã được backend kiểm tra.')
h('1.2 Mục tiêu và phạm vi')
p('Mục tiêu là triển khai một ứng dụng Java full-stack có khả năng chạy local, có dữ liệu bền vững, có phân quyền và có minh chứng kiểm thử. Trang khách chạy độc lập tại cổng 5173, trang admin tại 5174, cùng sử dụng API Java cổng 8080 và CSDL XAMPP cổng 3306.')
p('Phạm vi hiện tại là bán vé xem phim tại rạp minh họa. Tên phim, hình ảnh poster, địa điểm rạp và lịch mẫu phục vụ đồ án; hệ thống chưa kết nối kho ghế của rạp thương mại. Không có chức năng xem toàn bộ phim trực tuyến. Trailer nguồn mở được sử dụng để giới thiệu và kiểm tra phát nội dung ngắn.')
table(['Nhóm yêu cầu','Nội dung và trạng thái'],[
('Tài khoản','Đăng ký, đăng nhập ngay, refresh token, hồ sơ và đặt lại mật khẩu đã triển khai. Không bắt buộc xác thực email khi đăng ký.'),('Khách hàng','Tìm phim, lọc, xem chi tiết, chọn rạp/suất/ghế, giữ ghế, xem đơn và vé đã triển khai.'),('Thanh toán','Đã tích hợp create/query/webhook payOS; phải đối soát đủ tiền trước khi phát vé. Không thực hiện chuyển tiền trong lần chụp ảnh báo cáo.'),('Quản trị','CRUD phim, rạp, phòng, ghế, suất; xem giao dịch, nhật ký và soát vé đã triển khai.'),('Ngoài phạm vi','Chưa có hoàn tiền tự động, combo, khuyến mãi, hóa đơn thuế, kết nối rạp thương mại và triển khai production.')])
h('1.3 Yêu cầu phi chức năng')
p('Giao diện khách sử dụng tiếng Việt, thích ứng máy tính và điện thoại. Mọi dữ liệu giá, quyền truy cập, trạng thái ghế và thanh toán phải được kiểm tra ở backend. Các thao tác ghi quan trọng phải có giao dịch, khóa và ràng buộc CSDL; thông báo lỗi không được lộ stack trace hoặc khóa tích hợp. Chưa thực hiện kiểm thử tải để cam kết một mức thông lượng hay SLA production.')
h('1.4 Công nghệ sử dụng')
table(['Công nghệ','Vai trò thực tế'],[('Java 21 và Spring Boot 3.5.16','Java cài đặt nghiệp vụ; Spring Boot cấu hình ứng dụng, cung cấp HTTP API và tích hợp các module.'),('Spring Data JPA và JdbcTemplate','JPA quản lý entity/repository; JDBC dùng khi cần truy vấn báo cáo và khóa hàng rõ ràng.'),('MariaDB 10.4.32 trong XAMPP','CSDL local đang chạy. Có hỗ trợ MySQL 8.4 trong cấu hình Compose.'),('Flyway và Maven','Flyway quản lý 5 migration. Maven quản lý dependency, chạy JUnit và đóng gói JAR.'),('Spring Security, JWT, BCrypt','Phân quyền, access token và băm mật khẩu; refresh token được lưu dạng hash.'),('React, TypeScript và Vite','Xây dựng hai ứng dụng web; dùng chung components và design tokens.'),('TanStack Query và Recharts','Quản lý dữ liệu API phía giao diện và hiển thị thống kê admin.'),('JUnit, MockMvc, Mockito, Playwright','Kiểm thử nghiệp vụ/API và thao tác trình duyệt thật.'),('payOS Java SDK 2.0.1','Tạo link thanh toán, xác minh chữ ký và truy vấn trạng thái giao dịch.'),('Mailpit','Hộp thư thử local; chưa cấu hình email Internet theo phạm vi hiện tại.')])
h('CHƯƠNG 2  CƠ SỞ LÝ THUYẾT',1)
h('2.1 Spring Boot và tổ chức nghiệp vụ')
p('Spring Boot tạo môi trường chạy ứng dụng dựa trên dependency và cấu hình. Dependency injection cung cấp BookingService, PaymentService và các repository cho controller. Controller nhận DTO có validation, gọi service rồi trả DTO JSON; không trả trực tiếp JPA entity. Cách tổ chức này tách hợp đồng API khỏi cấu trúc persistence và giúp kiểm thử nghiệp vụ độc lập hơn với giao diện.')
p('Ứng dụng dùng kiến trúc modular monolith: một backend được phân chia thành các module auth, users, catalog, booking, payments, email, admin, security và shared. Các module vẫn được đóng gói trong cùng JAR, phù hợp quy mô đồ án; chưa sử dụng microservices hoặc hạ tầng hàng đợi phân tán.')
h('2.2 REST API và mã trạng thái HTTP')
p('REST API sử dụng tài nguyên và phương thức HTTP để diễn đạt thao tác. GET /movies lấy danh sách; POST /bookings tạo một đơn giữ ghế; POST /payments nhận bookingId để backend tạo giao dịch. Client không được gửi một giá trị total rồi yêu cầu backend chấp nhận làm tổng tiền chính thức.')
p('Ứng dụng trả 201 khi tạo hold thành công, 400 khi validation sai, 401 khi chưa đăng nhập, 403 khi sai quyền, 404 khi không có tài nguyên, 409 khi ghế vừa bị giữ và 503 khi tích hợp chưa sẵn sàng. DTO lỗi cung cấp thông báo để giao diện xử lý mà không lộ thông tin nội bộ.')
h('2.3 Lưu trữ và giao dịch')
p('Entity và repository hỗ trợ lưu tài khoản, phim, token và payment order. JdbcTemplate được dùng cho dữ liệu rạp, ghế, đặt vé và các truy vấn thống kê. Flyway áp dụng schema theo thứ tự và ghi lịch sử migration; Hibernate chỉ validate schema thay vì tự ý sửa bảng khi khởi động.')
p('Transaction phải bảo vệ cả bước kiểm tra và bước ghi. Nếu chỉ đọc ghế trống rồi insert ngoài transaction, hai request có thể cùng nhìn thấy ghế còn trống. CineVerse khóa hàng suất chiếu và sử dụng unique(showtime_id, seat_id) trên seat_reservations; transaction thứ hai phải đọc lại trạng thái sau khi chờ khóa.')
h('2.4 Bảo mật')
p('Mật khẩu được băm BCrypt. Access token JWT có thời hạn 15 phút được giữ trong bộ nhớ ứng dụng web. Refresh token ngẫu nhiên nằm trong cookie HttpOnly/SameSite Strict; CSDL lưu hash, xoay token khi refresh và thu hồi phiên khi phát hiện replay. Các request API admin cần vai trò ADMIN; dữ liệu đơn/vé còn được kiểm tra chủ sở hữu ở service.')
p('Hai cổng localhost không cô lập cookie theo cổng. Vì vậy hệ thống dùng cv_refresh cho trang khách và cv_admin_refresh cho trang admin, chọn dựa trên Origin cho phép. Việc tách hai giao diện không thay thế kiểm quyền backend. Cấu hình HTTPS production cần bật Secure cookie và CORS đúng địa chỉ triển khai.')
h('2.5 Các thành phần bổ sung')
p('Scheduler định kỳ dọn hold hết hạn và đối soát thanh toán pending. TanStack Query tải lại catalog khi focus hoặc theo chu kỳ khoảng 30 giây; form admin đang soạn không bị polling ghi đè. Outbox ghi tác vụ email trong transaction, rồi SMTP worker gửi bên ngoài transaction nghiệp vụ. Module SMTP hiện phục vụ thử local, không là điều kiện hoàn tất đăng ký hoặc mua vé.')
h('CHƯƠNG 3  PHÂN TÍCH VÀ THIẾT KẾ HỆ THỐNG',1)
h('3.1 Tác nhân và chức năng')
table(['Tác nhân','Chức năng','Trạng thái'],[('Khách vãng lai','Xem phim, lịch chiếu, rạp, trailer; tìm kiếm và lọc','Đã làm'),('Khách đăng nhập','Giữ/hủy ghế, checkout, xem đơn/vé, hồ sơ, yêu thích và đánh giá','Đã làm'),('Quản trị viên','Quản lý phim/rạp/phòng/ghế/suất, dashboard, đơn/vé, audit và check-in','Đã làm'),('Nhân viên riêng','Vai trò STAFF độc lập','Không áp dụng; check-in dùng ADMIN'),('payOS','Cung cấp link, trạng thái và webhook có chữ ký','Đã tích hợp; cần khóa riêng'),('Dịch vụ email','Gửi thông tin qua SMTP Internet','Chưa kích hoạt; local dùng Mailpit')])
diagram('d-usecase.png',{'guest':((30,100,350,260),'Khách vãng lai\nXem và tìm phim'),'user':((30,420,350,600),'Khách đăng nhập\nGiữ ghế và xem vé'),'admin':((1120,250,1460,460),'Quản trị viên\nQuản lý và soát vé'),'catalog':((530,70,950,250),'Catalog và lịch chiếu\nChi tiết và trailer'),'booking':((530,340,950,540),'Giữ ghế → thanh toán\nĐối soát → phát vé'),'manage':((530,650,950,820),'CRUD và thống kê\nNhật ký và check in')},[('guest','catalog',''),('user','booking',''),('admin','manage','')])
fig('d-usecase.png','Use Case tổng quát theo nhóm tác nhân','Mũi tên biểu diễn nhóm chức năng mà tác nhân được sử dụng. Khách phải đăng nhập trước khi giữ ghế; admin có quyền quản trị do backend cấp, không phải do tự chọn trên giao diện.')
h('3.2 Đầu vào và đầu ra nghiệp vụ')
table(['Nghiệp vụ','Đầu vào và xử lý','Kết quả'],[('Đăng ký','name, email, password, terms; kiểm hợp lệ, email unique, BCrypt','Tạo USER, có thể đăng nhập ngay'),('Giữ ghế','showtimeId, 1–8 seatIds; kiểm phòng/giờ/quyền, khóa suất, tính giá','Booking HELD, giá snapshot, hạn 8 phút'),('Tạo thanh toán','bookingId thuộc người dùng; backend lấy total và chọn gateway','Order và URL payOS hoặc lỗi readiness'),('Đối soát','Webhook hợp lệ hoặc query theo quyền; kiểm chữ ký/code/amount/link','Đủ tiền: PAID và phát vé; thiếu tiền: chưa phát vé'),('Check-in','Mã vé; ADMIN; khóa ticket và kiểm ISSUED','USED một lần; lần sau bị từ chối')])
diagram('d-architecture.png',{'customer':((40,40,650,200),'React khách 5173\nRouter và phiên khách'),'admin':((850,40,1460,200),'React admin 5174\nRouter và phiên quản trị'),'api':((420,330,1080,510),'Spring Boot API 8080\nSecurity → Controller → Service'),'db':((40,710,570,860),'XAMPP MariaDB 3306\nJPA JDBC Flyway'),'pay':((930,710,1460,860),'payOS API\nChữ ký và đối soát')},[('customer','api',''),('admin','api',''),('api','db',''),('api','pay','')])
fig('d-architecture.png','Kiến trúc triển khai local','Hai frontend gọi cùng backend, còn backend là thành phần duy nhất truy cập SQL và khóa tích hợp. Cấu hình bí mật không được gửi xuống React.')
h('3.3 Thiết kế cơ sở dữ liệu')
p('Schema hiện có 32 bảng tính cả flyway_schema_history, được hình thành bởi 5 migration. V1 lưu nền tảng ban đầu; V2 bổ sung email token; V3 thêm bán vé; V4 hủy email xác thực đăng ký đang chờ; V5 lưu ánh xạ order code và link payOS. Các bảng legacy seasons/episodes/subscription còn được giữ vì tương thích dữ liệu, nhưng không phải luồng sản phẩm bán vé hiện tại.')
nodes={}; names=[('users','id PK\nemail UNIQUE'),('bookings','id PK\nuser_id showtime_id FK'),('tickets','id PK\nbooking_id seat_id FK'),('movies','id PK\nslug UNIQUE'),('showtimes','id PK\nmovie_id auditorium_id FK'),('payment_orders','id PK\nbooking_id UNIQUE FK'),('cinemas','id PK'),('auditoriums','id PK\ncinema_id FK'),('payos_links','order_code PK\norder_id UNIQUE FK'),('seats','id PK\nauditorium_id FK'),('seat_reservations','id PK\nshowtime_id seat_id UNIQUE'),('booking_items','id PK\nbooking_id seat_id FK')]
for i,(name,attrs) in enumerate(names):
 col=i%3;row=i//3;nodes[name]=((20+col*500,20+row*280,470+col*500,220+row*280),name+'\n'+attrs)
diagram('d-erd.png',nodes,[('users','bookings',''),('bookings','tickets',''),('movies','showtimes',''),('bookings','payment_orders',''),('cinemas','auditoriums',''),('payment_orders','payos_links',''),('auditoriums','seats','')],(1500,1140))
fig('d-erd.png','Quan hệ dữ liệu cốt lõi dạng lược đồ rút gọn','PK là khóa chính; FK là khóa ngoại. Mũi tên nối nhóm quan hệ trọng tâm, không thay thế toàn bộ FK trong các migration. bookings tham chiếu showtimes; payment_orders tham chiếu bookings; seats tham chiếu auditoriums. Các ràng buộc quan trọng được trình bày trong bảng dưới.')
table(['Bảng','Khóa và quan hệ','Vai trò và ràng buộc'],[('users','id; email unique','Tài khoản, role, locked và session_version'),('movies','id; country_id FK','Metadata, trạng thái công bố và đường dẫn artwork'),('cinemas / auditoriums','id; auditorium.cinema_id FK','Rạp/phòng; tên phòng unique trong rạp'),('seats','id; auditorium_id FK','Ghế, hàng, số, loại; unique ghế trong phòng'),('showtimes','id; movie_id, auditorium_id FK','Suất, thời gian, giá; service khóa phòng để chống overlap'),('bookings','id; user_id, showtime_id FK','Trạng thái, tổng snapshot và hold_until'),('booking_items','id; booking_id, seat_id FK','Tên/loại/giá ghế snapshot; unique booking và ghế'),('seat_reservations','id; showtime_id, seat_id FK','Unique suất và ghế là rào cản bán trùng cuối cùng'),('payment_orders','id; booking_id unique FK','Số tiền, provider, trạng thái và giao dịch provider unique'),('payos_links','order_code PK; order_id unique FK','Ánh xạ mã số provider với mã đơn nội bộ'),('tickets','id; booking_id, seat_id FK','Code unique; unique booking/seat; ISSUED hoặc USED'),('email_outbox / audit_logs','id; dedup_key unique ở outbox','Lưu tác vụ nền và dấu vết hoạt động; không lưu secret')])
h('3.4 Quy tắc nhất quán và luồng thanh toán')
p('Một người được giữ tối đa 3 đơn đang hiệu lực, mỗi đơn tối đa 8 ghế trong 8 phút. Giá ghế bằng giá suất cộng phụ thu 20.000 VND nếu là VIP. Đóng bán trước giờ chiếu 10 phút. Snapshot giúp thay đổi cấu hình giá sau này không làm đổi số tiền của đơn đã tạo.')
p('Quy trình chính: chọn phim → rạp/ngày/suất → sơ đồ ghế → tạo hold → tạo payment order → sang payOS → backend kiểm chữ ký và query trạng thái → phát vé nếu đủ tiền và còn reservation hợp lệ. Return URL chỉ đưa khách về giao diện; không xác nhận PAID từ tham số trên trình duyệt.')
p('Nếu tiền được xác nhận sau khi hold đã hết hạn hoặc bị hủy, order phản ánh trạng thái PAID của provider nhưng booking chuyển PAYMENT_REVIEW. Hệ thống không chiếm lại ghế của người khác, không tự phát vé trong tình huống đó và chưa có refund tự động.')
h('3.5 Hợp đồng REST API tiêu biểu')
table(['Method và endpoint','Đầu vào → đầu ra','Quyền'],[('POST /auth/register','Thông tin đăng ký → thông báo tạo tài khoản','Public'),('POST /auth/login','Email/password → access token và cookie refresh','Public'),('GET /movies','Bộ lọc/page/size → danh sách có phân trang','Public'),('GET /showtimes/{id}/seats','Mã suất → trạng thái ghế và giờ server','Public'),('POST /bookings','showtimeId, seatIds → hold và total','USER'),('POST /bookings/{id}/cancel','Mã booking → hủy và trả ghế','Chủ đơn'),('POST /payments','bookingId → OrderView/payUrl','Chủ đơn'),('POST /payments/{id}/reconcile','Mã đơn → trạng thái được đối soát','Chủ đơn/ADMIN'),('POST /payments/payos/webhook','Payload ký → xử lý và xác nhận nhận','Chữ ký hợp lệ'),('GET /bookings/{id}','Mã đơn → thông tin và vé','Chủ đơn/ADMIN'),('PUT /admin/movies/{id}','MovieInput hợp lệ → MovieView','ADMIN'),('POST /admin/tickets/check-in','code → kết quả soát vé','ADMIN')])
p('Các endpoint trong bảng có tiền tố /api/v1. Swagger ở /swagger-ui/index.html và OpenAPI JSON ở /v3/api-docs. Các resource còn lại được mô tả trong docs/API_DOCUMENTATION.md của mã nguồn.')
h('CHƯƠNG 4  CÀI ĐẶT VÀ TRIỂN KHAI HỆ THỐNG',1)
h('4.1 Môi trường và cấu trúc mã nguồn')
p('Bản minh chứng chạy trên Windows, JDK 21, Spring Boot 3.5.16 và MariaDB 10.4.32 từ XAMPP. Maven launcher ở scripts/maven.ps1; frontend khóa phiên bản dependency bằng package-lock.json. Không yêu cầu IDE cố định; báo cáo không khẳng định đã sử dụng một IDE mà chưa có minh chứng.')
table(['Thư mục','Nội dung'],[('backend/src/main/java','Các module nghiệp vụ, controller, DTO, entity, repository và cấu hình bảo mật'),('backend/src/main/resources','application.yml, 5 migration, catalog seed và template email'),('backend/src/test','JUnit/MockMvc/Mockito và H2 độc lập'),('frontend/src','React khách và components/styles/features dùng chung'),('admin','Entry point và cấu hình build riêng cho quản trị'),('frontend/public/art','40 poster điện ảnh cùng artwork dự án; nguồn/prompt trong docs'),('scripts','Khởi tạo môi trường, XAMPP, Maven, start/stop và thiết lập tích hợp'),('docs','Kiến trúc, API, ERD/UML, báo cáo, bằng chứng kiểm thử và ảnh')])
h('4.2 Đoạn mã giữ ghế và cách áp dụng transaction')
p('Ví dụ rút gọn từ BookingService: thời hạn giữ được tính trên server, không tin đồng hồ client. Đoạn mã này chỉ minh họa một phần; kiểm quyền, khóa suất và unique constraint vẫn là điều kiện bắt buộc của toàn luồng.')
code=doc.add_paragraph();code.paragraph_format.line_spacing=1.0;code.alignment=WD_ALIGN_PARAGRAPH.LEFT
run=code.add_run('Instant now = Instant.now();\nInstant until = now.plusSeconds(480);\n// INSERT booking với total từ snapshot ghế\n// INSERT reservation trong cùng transaction');run.font.name='Consolas';run.font.size=Pt(11)
p('Service dùng @Transactional để nếu một ghế không hợp lệ hoặc insert reservation vi phạm unique, các thay đổi của request không được commit từng phần. Trình duyệt có bộ đếm ngược để hỗ trợ thao tác, nhưng backend mới quyết định hold còn hiệu lực hay không.')
h('4.3 Các màn hình chức năng phía khách')
p('Các hình sau được chụp trực tiếp ngày 06/11/2026 từ phiên local đang chạy, không phải bản vẽ giao diện. Các thao tác chụp sử dụng tài khoản demo; hold tạo phục vụ chụp đã được hủy sau khi lấy hình. Không tạo giao dịch hoặc chuyển tiền ngân hàng trong lần chụp.')
screens=[
('01-home.png','Trang chủ CineVerse','Trang chủ lấy dữ liệu catalog từ API, giới thiệu phim nổi bật và dẫn đến lịch chiếu. Poster là artwork AI cho phim hư cấu, đã được ghi nguồn trong tài liệu dự án.'),
('02-discover.png','Khám phá phim và bộ lọc','Tìm kiếm, sắp xếp và phân trang được gửi về backend. Hai tựa test/E2E là dữ liệu kiểm thử còn tồn tại trong DB, không phải phim thương mại.'),
('03-detail.png','Chi tiết phim và thao tác đặt vé','Trang chi tiết hiển thị metadata, thể loại, trailer và lịch. Nút đặt vé đưa người dùng tới chọn suất; trailer minh họa không phải nội dung phim hư cấu trên poster.'),
('04-cinemas.png','Danh sách rạp và lịch chiếu','Thông tin rạp/phòng/suất được đọc cùng CSDL với trang admin. Khi admin thay đổi dữ liệu hợp lệ, trang khách tải lại dữ liệu từ API.'),
('05-register.png','Biểu mẫu đăng ký tài khoản','Form yêu cầu các trường hợp lệ và đồng ý điều khoản. Theo phạm vi hiện tại, đăng ký xong được đăng nhập ngay, không phải nhập mã xác thực email.'),
('06-login.png','Đăng nhập khách hàng','Tài khoản trong hình là dữ liệu demo; mật khẩu được che. Sau đăng nhập, access token được giữ trong bộ nhớ, refresh cookie do backend phát hành.'),
('07-account.png','Không gian tài khoản khách','Khách xem và cập nhật thông tin của mình. Backend xác định user từ phiên đăng nhập, không tin một userId do client tự nhận.'),
('09-seats.png','Lựa chọn ngày và suất chiếu','Khách xem ngày, rạp và giờ chiếu trước khi mở sơ đồ ghế. Số ghế trống và giá suất là dữ liệu hiện tại của hệ thống.'),
('19-seatmap.png','Sơ đồ ghế đang chạy','Màn hình phân biệt các trạng thái ghế và cập nhật giá theo lựa chọn. Chọn ghế ở giao diện chưa đồng nghĩa đã mua; bước giữ ghế trên server còn phải kiểm tra cạnh tranh.'),
('10-checkout.png','Checkout và thời hạn giữ ghế','Hình cho thấy hold 8 phút, mã ghế và tổng 95.000 VND lấy từ backend. payOS đã nạp cấu hình trên máy, nhưng hình này chỉ chứng minh màn hình checkout, không chứng minh giao dịch ngân hàng thành công.'),
('08-tickets.png','Danh sách đơn và vé của khách','Danh sách lấy theo chủ tài khoản. Lịch sử có thể gồm hold đã hủy, giao dịch hồi quy và vé đã phát hành; không được tính toàn bộ là doanh thu payOS thật.'),
('20-ticket-detail.png','Chi tiết đơn vé hiện có','Đây là dữ liệu vé đã tồn tại trong DB. Nguồn thanh toán phải được phân biệt theo provider; ảnh không được sử dụng để tuyên bố vừa thực hiện một giao dịch ngân hàng trong lần chụp.'),
('21-qr.png','Mã QR của vé hiện có','QR đại diện cho mã vé ngẫu nhiên. Chỉ chủ đơn hoặc admin đọc được; khi vào rạp, backend khóa vé và chỉ chấp nhận chuyển ISSUED sang USED một lần.')]
for fn,title,body in screens:fig(fn,title,body)
h('4.4 Các màn hình quản trị')
p('Ứng dụng admin là entry point riêng tại cổng 5174. Các hình được chụp sau khi đăng nhập tài khoản ADMIN của dự án. Form chỉnh sửa chỉ được mở để minh chứng, không lưu thay đổi khi chụp.')
for fn,title,body in [
('11-dashboard.png','Tổng quan quản trị từ CSDL','Dashboard tổng hợp thành viên, phim, vé và giao dịch. Giá trị payOS, Sandbox và simulation được thống kê riêng để tránh trình bày tiền thử nghiệm như tiền thật.'),
('12-movies.png','Quản lý phim và poster','Admin tìm kiếm, thêm, sửa và ẩn phim qua API Java. Danh sách hiển thị poster mới của 40 phim gốc; hai phim test/E2E giữ ảnh cũ theo yêu cầu.'),
('18-editor.png','Biểu mẫu chỉnh sửa phim','MovieInput có validation ở server. Metadata được chỉnh qua service có audit; thao tác giao diện không trực tiếp ghi SQL từ trình duyệt.'),
('13-showtimes.png','Quản lý suất chiếu','Admin chọn phim, phòng, giờ và giá. Backend tính thời gian kết thúc theo độ dài phim cộng thời gian đệm và từ chối suất trùng phòng/giờ.'),
('14-cinemas.png','Quản lý rạp','Dữ liệu rạp phục vụ cả hai ứng dụng. Không xóa lịch sử vé đã phát hành bằng cách xóa trực tiếp bản ghi quan hệ.'),
('15-payments.png','Theo dõi giao dịch','Trang thể hiện provider, số tiền và trạng thái. Trạng thái chính thức do backend cập nhật sau đối soát, không do giao diện tự gán khi khách quay lại từ provider.'),
('16-checkin.png','Chức năng soát vé','Admin nhập mã vé hoặc dùng thiết bị quét dạng bàn phím. Hình chứng minh màn hình chức năng, không phải minh chứng quét camera trên điện thoại.'),
('17-audit.png','Nhật ký hoạt động','Audit lưu tác nhân, hành động và đối tượng bị thay đổi để truy vết. Không ghi mật khẩu và khóa tích hợp vào nhật ký.')]:fig(fn,title,body)
h('4.5 Tích hợp payOS và trạng thái xác minh')
p('PayosPaymentGateway dùng SDK chính thức để ký request và xác minh phản hồi; kiểm thêm orderCode, amount, paymentLinkId và host checkout. PayosReconciliationService xác minh webhook rồi truy vấn trạng thái provider; thao tác đối soát từ khách kiểm tra quyền chủ đơn và giới hạn tần suất. Khi PAID phải đủ số tiền mới gọi fulfillment; UNDERPAID không phát vé.')
p('payOS không cung cấp Sandbox tiền ảo. Chủ máy đã nhập khóa riêng bằng công cụ local; khóa không nằm trong báo cáo hoặc bản nộp. Việc nạp đủ cấu hình không thay thế bằng chứng ngân hàng chấp nhận credential hay một giao dịch đã hoàn tất. Báo cáo chưa có ảnh giao dịch ngân hàng thành công được xác minh. MoMo/VNPAY Sandbox và simulation còn ở code để hồi quy dev/test; không được trình bày như provider đang được chọn.')
h('4.6 Hướng dẫn chạy')
for t in ['1. Cài JDK 21, Node.js 22.12+ hoặc 24, PowerShell 7, XAMPP và Mailpit cho SMTP thử local.', '2. Giải nén mã nguồn, mở PowerShell 7 tại thư mục gốc. Chạy pwsh -File scripts/setup-env.ps1 để tạo secret mới.', '3. Đặt XAMPP_ROOT trong .env đúng thư mục cài đặt. Dùng scripts/setup-xampp-db.ps1 cho database cineverse chưa tồn tại; script từ chối ghi đè dữ liệu có sẵn.', '4. Chạy pwsh -File scripts/start-local.ps1 -MailpitExe C:/tools/mailpit.exe, thay đường dẫn theo máy. Flyway và seed sẽ khởi tạo schema/dữ liệu.', '5. Mở khách http://localhost:5173 và admin http://localhost:5174. Xem mật khẩu demo mới bằng scripts/show-demo.ps1; không dùng mật khẩu máy sinh viên.', '6. Khi đã build bản hiện tại, có thể khởi động lại bằng -SkipBuild. Dừng bằng scripts/stop-local.ps1; không xóa CSDL để xử lý lỗi.']:p(t)
p('Tài khoản demo gồm admin@cineverse.local, free@cineverse.local và vip@cineverse.local. Tên vip là dữ liệu legacy, không có quyền chiếm ghế của người khác. Bản nộp không kèm khóa payOS, .env thật hoặc dữ liệu tài khoản khách của máy sinh viên; người chạy tự cấu hình tích hợp nếu cần. Chưa xác minh bản nộp trên một máy sạch khác.')
h('CHƯƠNG 5  KIỂM THỬ HỆ THỐNG',1)
h('5.1 Phương pháp và môi trường')
p('JUnit, MockMvc và Mockito kiểm tra service/API với H2 MySQL mode và transport giả có chủ đích. Playwright thao tác Chromium với backend, MariaDB XAMPP và Mailpit thật. Test H2 không chứng minh mọi đặc điểm khóa của MariaDB; vì vậy đồ án có thêm trường hợp hai user tranh một ghế trên CSDL đang chạy.')
p('Bằng chứng Maven gần nhất trong tài liệu phát triển đạt 80 kiểm thử, không lỗi; xem log final-java-tests.log ngày 05/11/2026. Lượt browser đầy đủ tách hai trang có 11 đạt, 2 assertion lỗi và 3 test không áp dụng; sau khi sửa assertion URL logout/errorCode đã chạy lại bộ 4 test tách trang đạt 4/4. Không gộp các lượt thành một lần chạy toàn bộ đều xanh. Chi tiết và giới hạn ở docs/TEST_PLAN.md.')
h('5.2 Bảng test case tiêu biểu')
table(['STT','Nghiệp vụ và dữ liệu','Mong đợi','Kết quả có bằng chứng'],[
('01','Đăng ký hợp lệ, chưa xác thực email','Đăng nhập ngay','Đạt trong hồi quy browser'),('02','USER gọi API admin','HTTP 403','Đạt trong integration test'),('03','Người khác đọc bookingId','Bị từ chối','Đạt trong API/race test'),('04','Hai user giữ cùng suất/ghế','Một thành công, một 409','Đạt trên MariaDB XAMPP'),('05','Ghế thuộc phòng khác','HTTP 400, không tạo hold','Đạt trong integration test'),('06','Hết hạn/hủy hold','Reservation được giải phóng','Đạt trong integration test'),('07','Client tự gửi giá','Backend dùng snapshot','Đạt trong test nghiệp vụ giá'),('08','Thanh toán lặp cùng đơn','Một bộ vé/biên nhận','Đạt trong test idempotency'),('09','payOS UNDERPAID','Không phát vé','Đạt với dữ liệu provider kiểm thử'),('10','payOS sai amount/link/chữ ký','Từ chối settle','Đạt với SDK/transport kiểm thử'),('11','Tiền đến sau khi hủy/hết hạn','PAYMENT_REVIEW, không chiếm ghế','Đạt trong integration test'),('12','Check-in mã vé hai lần','Lần sau bị từ chối','Đạt trong test nghiệp vụ'),('13','Admin và khách cùng browser','Phiên độc lập sau logout admin','Đạt trong bộ 4 test tách trang'),('14','Admin sửa phim','Trang khách nhận thay đổi','Đạt trên CSDL chung, có phục hồi dữ liệu'),('15','40 URL poster và hai tựa loại trừ','Ảnh mới đúng, hai tựa giữ nguyên','Đạt 40 HTTP 200; 0 lỗi JS trong QA poster'),('16','Thanh toán ngân hàng thật payOS','Đối soát PAID và vé hợp lệ','Chưa có bằng chứng end-to-end ngân hàng trong báo cáo'),('17','Email Internet / Docker / tải cao','Gửi thư, triển khai và tải theo yêu cầu','Chưa xác minh; không kết luận đã đạt')])
h('5.3 Kiểm tra trực tiếp cho báo cáo')
p('Ngày 06/11/2026, đã khởi động backend health UP và hai frontend HTTP hoạt động, đăng nhập khách/admin, mở các trang chức năng và chụp ảnh thật. Đã tạo một hold để chụp checkout rồi hủy bằng API chủ đơn; việc chụp không gọi API tạo thanh toán, không chuyển tiền và không phát hành vé mới. Danh mục ảnh và URL được lưu tại docs/report/screenshots/evidence.json.')
p('Các ảnh được chèn là minh chứng màn hình đang chạy. Kết quả chống trùng ghế, kiểm chữ ký và idempotency dựa trên kiểm thử có log, không suy luận chỉ từ giao diện. Dữ liệu TEST/SIMULATION trong lịch sử cần giữ nhãn khi thuyết trình.')
h('CHƯƠNG 6  KẾT QUẢ ĐÁNH GIÁ VÀ HƯỚNG PHÁT TRIỂN',1)
h('6.1 Kết quả đạt được')
p('CineVerse đã triển khai luồng bán vé nhất quán từ catalog đến giữ ghế, checkout, đối soát, vé và check-in; có giao diện quản trị độc lập dùng chung CSDL. Các quy tắc giá, quyền và trạng thái được đặt ở Java service. Hình ảnh 40 phim được thay bằng poster điện ảnh tạo riêng cho dữ liệu hư cấu, giúp giao diện dễ quan sát hơn khi trình diễn.')
p('Về Java/Spring Boot, đồ án thể hiện dependency injection, Java records cho DTO, interface gateway, repository, transaction, validation, scheduler và security filter chain. Điểm cần bảo vệ khi phản biện là sự phối hợp giữa service và constraint SQL, không chỉ khả năng hiển thị trang web.')
h('6.2 Hạn chế')
p('Ứng dụng mới phục vụ một instance local; rate limit và token truy cập trailer còn trong bộ nhớ. Chưa có load test, quan sát production, cơ chế hoàn tiền tự động hoặc đồng bộ ghế với nhà rạp thương mại. Chưa có bằng chứng ngân hàng end-to-end payOS trong báo cáo. Gửi email Internet không được cấu hình theo phạm vi hiện tại; Mailpit chỉ chứng minh SMTP local.')
p('Dữ liệu mẫu có thời hạn lịch chiếu và các bản ghi kiểm thử còn xuất hiện. Poster AI và trailer minh họa không phải nội dung phát hành của phim thật. Các bảng legacy còn tồn tại vì không phá dữ liệu cũ; trước vận hành thương mại cần kế hoạch migration và rà soát phạm vi sử dụng.')
h('6.3 Hướng phát triển')
p('Ưu tiên bổ sung quy trình hỗ trợ PAYMENT_REVIEW và hoàn tiền có kiểm soát; quản lý khuyến mãi/combo; tài khoản nhân viên có quyền riêng; đối soát tập trung và quan sát lỗi tích hợp. Khi có yêu cầu vận hành thật, cần HTTPS, secret manager, rate limit phân tán, kiểm thử tải, backup/restore và đánh giá bảo mật độc lập.')
h('KẾT LUẬN',1)
p('Đồ án cho thấy một website bán vé cần đồng thời giải quyết giao diện, dữ liệu và tính đúng đắn giao dịch. Java/Spring Boot đảm nhiệm nghiệp vụ và bảo mật; MariaDB cung cấp lưu trữ/ràng buộc; React thể hiện trạng thái và hỗ trợ thao tác. Bộ ảnh chụp trực tiếp giúp đối chiếu thiết kế với sản phẩm đang chạy, trong khi kiểm thử bổ sung bằng chứng cho các trường hợp khó quan sát bằng ảnh.')
p('Bản báo cáo phân biệt chức năng đã cài đặt, kết quả kiểm thử local và tích hợp bên ngoài chưa được xác minh đầy đủ. Khi nộp và bảo vệ, nhóm cần giải thích được các quyết định giữ ghế, snapshot giá, chống callback lặp và kiểm quyền theo chủ đơn, đồng thời cập nhật các thông tin nhóm chưa cung cấp.')
h('TÀI LIỆU THAM KHẢO',1)
for t in ['[1] Tài liệu học phần. Yêu cầu viết báo cáo đồ án môn Lập trình Java, bản PDF 4 trang do người học cung cấp; dùng làm yêu cầu cấu trúc và hình thức.', '[2] Mã nguồn CineVerse, Java 21 / Spring Boot 3.5.16, phiên bản local được kiểm tra ngày 06/11/2026. Các tệp ARCHITECTURE.md, DATABASE_ERD.md, API_DOCUMENTATION.md và TEST_PLAN.md trong docs.', '[3] Oracle. Java SE 21 API Specification. https://docs.oracle.com/en/java/javase/21/docs/api/ — tài liệu nền tảng được học phần giới thiệu.', '[4] Spring. Spring Boot Reference. https://docs.spring.io/spring-boot/reference/ — tham khảo vai trò cấu hình và ứng dụng web Servlet.', '[5] Spring. Spring Data JPA Reference. https://docs.spring.io/spring-data/jpa/reference/ — tham khảo repository và persistence.', '[6] Spring. Spring Security Reference. https://docs.spring.io/spring-security/reference/ — tham khảo xác thực, phân quyền và cookie/token.', '[7] payOS. Java SDK và môi trường test. https://payos.vn/docs/sdks/back-end/java/ ; https://payos.vn/docs/moi-truong-test/ — SDK Maven 2.0.1 đã đối chiếu trong quá trình tích hợp ngày 05/11/2026.', '[8] Blender Foundation. Sintel và Big Buck Bunny, CC BY 3.0. Nguồn và ghi công cụ thể trong docs/MEDIA.md.']:p(t)
h('PHỤ LỤC',1)
h('A Thông tin bản nộp và tài khoản thử')
p('Thư mục mã nguồn: JaVaBaoCao. Bản ZIP đã chuẩn bị tại NopBai/CineVerse-NopBai-20261105-231629.zip. README và HUONG-DAN-NOP-BAI.md hướng dẫn dựng lại dự án. Đường dẫn GitHub: chưa cung cấp; không tạo một URL kho giả. Bộ sưu tập Postman: không áp dụng; dự án cung cấp Swagger/OpenAPI.')
p('Các tài khoản demo: admin@cineverse.local (ADMIN), free@cineverse.local và vip@cineverse.local (USER). Mật khẩu không ghi trong báo cáo; sinh ngẫu nhiên và xem qua scripts/show-demo.ps1 sau khi cài đặt. Khóa thanh toán và secret không thuộc tài liệu nộp công khai.')
h('B Phân công nhóm cần xác nhận')
p('Thông tin sinh viên được cung cấp trực tiếp bởi người học. Chưa có xác nhận về phân công từng người, lịch sử commit hoặc tỷ lệ hoàn thành; bảng dưới để nhóm tự điền theo công việc thực tế trước khi nộp.')
table(['Thành viên','Nhiệm vụ thực tế','Minh chứng và mức độ'],[('Lê Thành Đạt\n2124110145','[Nhóm bổ sung]','[Nhóm xác nhận]'),('Huỳnh Minh Kha\n2124110175','[Nhóm bổ sung]','[Nhóm xác nhận]')])
h('C Danh mục minh chứng và lưu ý hoàn thiện')
p('Ảnh chụp chức năng nằm trong docs/report/screenshots; dấu thời gian/URL trong evidence.json. Hình Use Case, kiến trúc và lược đồ dữ liệu trong báo cáo được dựng từ thiết kế để giải thích, không phải ảnh màn hình web. Nhóm cần điền khoa, số nhóm và phân công thực tế; kiểm tra lại số trang mục lục trước khi in. Báo cáo được biên soạn với hỗ trợ công cụ, đối chiếu mã nguồn và ảnh chụp; nhóm vẫn cần đọc, hiểu và chịu trách nhiệm về nội dung khi bảo vệ.')
# Normalize theme residue so Word keeps the required academic typeface.
for style in doc.styles:
 pr=style.element.get_or_add_rPr()
 for old in list(pr):
  if old.tag==qn('w:rFonts'):pr.remove(old)
 fonts=OxmlElement('w:rFonts')
 for name in ['ascii','hAnsi','eastAsia','cs']:fonts.set(qn('w:'+name),'Times New Roman')
 pr.append(fonts)
 for border in list(style.element.iter(qn('w:pBdr'))):border.getparent().remove(border)
for border in list(doc.element.iter(qn('w:pBdr'))):border.getparent().remove(border)
doc.core_properties.title='Báo cáo đồ án website bán vé xem phim CineVerse'
doc.core_properties.author='Lê Thành Đạt; Huỳnh Minh Kha'
doc.core_properties.subject='Môn Lập trình Java – CCQ2411E'
path=OUT/'NhomXX_CineVerse_LapTrinhJava.docx';doc.save(path)
print(str(path));print('Screenshots embedded:',sum(1 for x in screens if (SHOTS/x[0]).exists())+8)

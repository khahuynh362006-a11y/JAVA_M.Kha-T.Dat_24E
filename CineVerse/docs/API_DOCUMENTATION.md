# REST API CineVerse

Prefix `/api/v1`. Swagger sinh từ code tại `/swagger-ui/index.html`; JSON tại `/v3/api-docs`. API trả DTO JSON, danh sách catalog/admin có `{content,totalElements,totalPages,page,size}`. Không trả password hash, refresh token DB hoặc secret MoMo.

## Auth và quyền

Thanh toán hiện chọn payOS. GET /payment-config trả mode/provider/ready/message, không trả khóa. POST /payments nhận {bookingId}, lấy giá từ server và tạo checkout URL đã ký sau khi kiểm owner. Khi thiếu khóa hoặc PAYOS_ENABLED=false: 503 PAYOS_NOT_CONFIGURED trước khi tạo order. POST /payments/payos/webhook public nhưng phải qua HMAC của SDK và query trạng thái provider; chỉ PAID đủ tiền mới phát vé. POST /payments/{id}/reconcile kiểm owner/admin rồi query payOS/VNPAY theo provider đơn. Return/cancel URL không được mark paid. Các route MoMo/VNPAY và simulate giữ để tương thích; simulate chỉ cho dev/test với mode=simulation.

| Method / path | Quyền | Payload / tác dụng |
|---|---|---|
| POST /auth/register | public, rate limited | name,email,password,terms,marketing; 201, có thể đăng nhập ngay; không gửi VERIFY |
| POST /auth/login | public | email,password,remember; accessToken/user và HttpOnly refresh cookie |
| POST /auth/refresh | cookie + Origin đúng | xoay refresh, access token mới |
| POST /auth/logout | Origin đúng | thu hồi refresh cookie hiện tại |
| POST /auth/verify-email | public | token một lần |
| POST /auth/resend-verification | public, tương thích cũ | trả thông báo không cần xác thực; không gửi thư |
| POST /auth/forgot-password | public | email; generic response |
| POST /auth/reset-password | public | token,password; thu hồi phiên |
| POST /auth/confirm-email-change | public | token email mới; thu hồi phiên |
| GET/PATCH /users/me | USER | xem/cập nhật name,marketing |
| PATCH /users/me/password | USER | currentPassword,password |
| POST /users/me/email-change | USER | email,password; gửi xác nhận email mới |
| POST /users/me/avatar | USER | multipart file ảnh |
| POST /users/me/logout-all | USER | tăng sessionVersion, thu hồi refresh |

Mutation có `Authorization: Bearer <accessToken>`. Refresh/logout không dùng Bearer làm cơ sở; cookie HttpOnly cần Origin chính xác: khách http://localhost:5173 dùng cv_refresh, admin http://localhost:5174 dùng cv_admin_refresh. Hai origin được cấu hình qua FRONTEND_URL/ADMIN_URL. Access token 15 phút; frontend giữ trong memory, không localStorage.

## Catalog và cộng đồng

- GET `/movies`: `q,genre,country,year,type,status,rating,screening,sort,page,size`; size tối đa 48. `screening=now|upcoming` dựa trên suất tương lai. `sort=new|popular|rating|az|featured`.
- GET `/movies/featured`, `/movies/trending`, `/movies/new`, `/movies/top-rated`, `/movies/search`, `/movies/{slug}`, `/movies/{id}/related`.
- GET `/genres`, `/countries`, `/banners`, `/settings`.
- GET/POST `/favorites`, DELETE `/favorites/{movieId}`; POST body `{movieId}`.
- GET `/movies/{movieId}/reviews`, POST cùng path `{stars:1..5,content}`. PATCH/DELETE `/reviews/{id}` chỉ chủ đánh giá. POST `/reviews/{id}/report` với reason.
- GET `/notifications`, PATCH `/notifications/{id}/read`, POST `/notifications/read-all`: chỉ người nhận hiện tại.
- GET `/movies/{id}/trailer` trả vé truy cập trailer ngắn hạn; media endpoint hỗ trợ Range. Không có route phát phim dài trong sản phẩm bán vé.

## Đặt vé

| Method / path | Quyền | Dữ liệu |
|---|---|---|
| GET /cinemas | public | rạp active |
| GET /showtimes | public | movieId,cinemaId,date (YYYY-MM-DD theo Việt Nam) |
| GET /showtimes/{id} | public | metadata suất/phòng/rạp/giá/số ghế |
| GET /showtimes/{id}/seats | public | showtime,seats,serverTime; mỗi ghế AVAILABLE/HELD/BOOKED |
| POST /bookings | USER | `{showtimeId,seatIds:[...]}`; 201 giữ tối đa 8 ghế/8 phút |
| GET /bookings/me hoặc /tickets/me | USER | 100 đơn mới nhất, gồm vé thuộc chủ đơn |
| GET /bookings/{id} | chủ đơn/ADMIN | snapshot ghế, tiền, suất, tickets, paymentOrderId |
| POST /bookings/{id}/cancel | chủ đơn | hủy đơn chưa xác nhận, trả ghế |
| GET /payment-config | public | mode payos/vnpay/momo/simulation/disabled, provider, ready, message |
| POST /payments/payos/webhook | public, HMAC | signed webhook; query trước khi settle |
| POST /payments/{id}/reconcile | chủ đơn/ADMIN | query provider; không tin trạng thái client |
| POST /payments hoặc /payments/momo/create | USER | **chỉ `{bookingId}`**, server lấy số tiền |
| POST /payments/{id}/simulate | chủ đơn, dev/test simulation | outcome success/failure/cancel |
| POST /payments/momo/ipn | public, HMAC | xác thực provider callback; 204 khi hợp lệ |
| GET /payments/{id} hoặc /status | chủ đơn/ADMIN | trạng thái chính thức từ backend |
| GET /users/me/payments | USER | chỉ orders purpose BOOKING |

Ví dụ đặt ghế và thanh toán:

```json
{"showtimeId":4,"seatIds":[1,2]}
```

Sau khi nhận booking ID:

```json
{"bookingId":"CVB-<uuid>"}
```

Không gửi total/price/status thành công từ frontend. `OrderView.planName` là tên snapshot hiển thị của phim, giữ tên field để tương thích schema ban đầu; `purpose=BOOKING` và `bookingId` xác định nghiệp vụ mới. Trang kết quả chỉ dùng query `orderId` để đọc trạng thái, không cấp vé từ query resultCode.

## Admin

Toàn bộ `/admin/**` yêu cầu ROLE_ADMIN. Các list có q,page,size và payments có status.

- GET `/admin/dashboard?days=7|30|90`, `/admin/analytics`: payosRevenue, sandboxRevenue, simulationRevenue tách riêng; chart có payos/revenue (Sandbox)/simulation.
- CRUD `/admin/movies`; GET chi tiết trả MovieInput để edit; DELETE soft delete.
- CRUD `/admin/genres`, `/admin/countries`, `/admin/banners`.
- CRUD `/admin/cinemas`: name,city,address,active.
- CRUD `/admin/auditoriums`: cinemaId,name,format,rows,columns,active; layout chỉ sinh khi tạo mới.
- GET `/admin/seats`, PUT `/admin/seats/{id}`: kind STANDARD/VIP,active; không sửa ghế đang reserved.
- CRUD `/admin/showtimes`: movieId,auditoriumId,startsAt (ISO Instant),basePrice,active. End tự tính duration +15 phút. Không sửa suất có đơn hiệu lực, không cho overlap.
- GET `/admin/bookings`, `/admin/bookings/{id}`, `/admin/tickets`.
- POST `/admin/tickets/check-in` với `{code}` hoặc nội dung `CINEVERSE:TICKET:<code>`; ghi USED một lần.
- GET/PATCH `/admin/users`, GET `/admin/payments/{id}`, GET `/admin/payments/export` (CSV tối đa 10.000 dòng).
- PATCH `/admin/comments/{id}`, POST `/admin/notifications`, GET `/admin/email-logs`, POST `/admin/email-logs/{id}/retry`, GET `/admin/audit-logs`, PATCH `/admin/settings`.

## Lỗi

Schema lỗi: timestamp,status,errorCode,message,path,validationErrors. Security filter trả status/errorCode/message gọn. 400 validation/token/signature sai; 401 chưa đăng nhập/phiên hết hạn; 403 sai quyền/Origin; 404 dữ liệu không có; 409 ghế đã được giữ hoặc trùng constraint; 413 upload quá lớn; 429 rate limit; 503 tích hợp chưa cấu hình. Không gửi stack trace cho client.

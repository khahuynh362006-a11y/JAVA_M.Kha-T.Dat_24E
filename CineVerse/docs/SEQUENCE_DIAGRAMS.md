# Sequence diagrams

## Đăng ký và đăng nhập ngay

```mermaid
sequenceDiagram
 actor U as Khách hàng
 participant R as React
 participant A as AuthService
 participant D as MySQL
 participant W as OutboxWorker
 participant M as Mailpit/SMTP
 U->>R: Đăng ký + đồng ý điều khoản
 R->>A: POST register
 A->>A: Validate + BCrypt
 A->>D: User + welcome notification/outbox (không VERIFY token)
 A-->>R: 201, có thể đăng nhập ngay
 W->>D: Claim email PENDING
 W->>M: SMTP HTML + plain text
 Note over U,M: Email chào mừng không chặn đăng nhập
```

## Đăng nhập và refresh rotation

```mermaid
sequenceDiagram
 participant R as React
 participant A as AuthService
 participant D as MySQL
 R->>A: email/password
 A->>D: Đọc tài khoản
 A->>A: BCrypt và locked (không yêu cầu verified)
 A->>D: Hash refresh mới, audit
 A-->>R: JWT 15 phút + HttpOnly cookie
 R->>A: Refresh cookie + Origin
 A->>D: Lock token, consumed=true
 A->>D: Hash refresh mới cùng family
 A-->>R: JWT/cookie xoay vòng
 Note over A,D: Replay token đã dùng sẽ thu hồi phiên
```

## Chọn suất, giữ ghế và thanh toán

```mermaid
sequenceDiagram
 actor U as Khách hàng
 participant R as React
 participant B as BookingService
 participant D as MySQL
 participant P as PaymentService
 participant G as PaymentGateway
 U->>R: Chọn suất và 2 ghế
 R->>B: POST bookings(showtimeId,seatIds)
 B->>D: Lock showtime, dọn expired holds
 B->>D: Kiểm tra ghế/phòng, insert snapshot + reservations
 Note over B,D: Unique(showtime_id,seat_id)
 B-->>R: Booking HELD, total, holdUntil
 R->>P: POST payments(bookingId)
 P->>B: Kiểm tra chủ đơn + thời hạn
 P->>D: Order PENDING, giá snapshot
 P->>G: Create signed payment
 G-->>P: payUrl
 P-->>R: URL cổng thanh toán
 R-->>U: Chuyển hướng thanh toán
```

## IPN MoMo và phát hành vé

```mermaid
sequenceDiagram
 participant M as MoMo Sandbox
 participant P as PaymentService
 participant B as BookingService
 participant D as MySQL
 participant W as OutboxWorker
 M->>P: IPN + HMAC
 P->>P: Verify signature/partner/request/amount/orderInfo
 P->>D: Lock payment order
 alt Đã PAID cùng transaction
  P-->>M: 204, không phát hành lại
 else Thanh toán resultCode=0
  P->>D: PAID + unique provider transaction
  P->>B: fulfillPaid
  B->>D: Lock showtime, kiểm tra reservations/thời hạn
  alt Ghế còn giữ hợp lệ
   B->>D: CONFIRMED + BOOKED + mỗi ghế 1 ticket + outbox
  else Giữ ghế đã hết hạn/hủy
   B->>D: PAYMENT_REVIEW + thông báo, không có vé
  end
  P-->>M: 204 sau commit
 end
 W->>D: Claim email
 W->>W: Gửi SMTP ngoài transaction nghiệp vụ
 Note over P,D: Redirect chỉ đọc trạng thái; không phát hành vé
```

## Hết hạn và soát vé

```mermaid
sequenceDiagram
 participant S as Scheduler
 participant D as MySQL
 participant A as Staff/Admin
 participant B as BookingService
 S->>D: Lock suất có hold quá hạn
 S->>D: Xóa reservations HELD, booking EXPIRED
 A->>B: POST check-in(code)
 B->>D: Lock ticket theo code
 B->>B: Kiểm tra ISSUED, suất không quá cũ
 B->>D: USED + usedAt + audit
 B-->>A: Ghế hợp lệ
 A->>B: Cùng code lần nữa
 B-->>A: 400, vé đã sử dụng
```

# Sơ đồ lớp Java

```mermaid
classDiagram
 class PaymentGateway {
  <<interface>>
  +provider() String
  +create(PaymentOrder) String
 }
 class MomoSandboxPaymentGateway {
  +create(PaymentOrder) String
  +verify(Map) void
  +query(PaymentOrder) Map
 }
 class MockPaymentGateway {
  +create(PaymentOrder) String
 }
 PaymentGateway <|.. MomoSandboxPaymentGateway
 PaymentGateway <|.. MockPaymentGateway
 class PaymentService {
  +createForBooking(userId,bookingId) OrderView
  +ipn(event) void
  +simulate(userId,orderId,outcome) OrderView
  -fulfill(order,transaction) void
 }
 class BookingService {
  +showtimes(movieId,cinemaId,date) List
  +seats(showtimeId) SeatMap
  +hold(userId,HoldInput) BookingView
  +prepareCheckout(userId,bookingId) BookingView
  +fulfillPaid(PaymentOrder) void
  +cancel(userId,id) void
  +checkIn(code,actor) TicketView
 }
 class AuthService {
  +register(Register) void
  +login(Login) SessionResult
  +refresh(rawToken) SessionResult
  +verify(rawToken) void
  +requestEmailChange(id,EmailChange) void
 }
 class CatalogService {
  +list(filters,admin) Paged
  +detail(slug) Detail
  +save(id,MovieInput,actor) MovieView
 }
 class EmailService { +queue(dedupKey,recipient,template,body) void }
 class OutboxWorker { +deliver() void }
 class Audit { +log(actor,action,type,id,detail) void }
 PaymentService --> PaymentGateway
 PaymentService --> BookingService
 PaymentService --> PaymentOrderRepository
 BookingService --> JdbcTemplate
 BookingService --> EmailService
 BookingService --> Audit
 AuthService --> UserRepository
 AuthService --> AuthTokenRepository
 AuthService --> EmailService
 CatalogService --> MovieRepository
 OutboxWorker --> JavaMailSender
 OutboxWorker --> JdbcTemplate
```

DTO là Java records trong AuthDtos/CatalogDtos/BookingDtos hoặc các record admin. Entity và repository JPA là lớp/interface riêng. Các quan hệ cinema/booking dùng FK và DTO thay vì serialize đồ thị entity; snapshot tránh thay đổi giá/tên ghế của vé đã mua.

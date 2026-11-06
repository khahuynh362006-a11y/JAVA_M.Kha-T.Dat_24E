package com.cineverse.booking;

import static com.cineverse.booking.BookingDtos.*;

import com.cineverse.email.EmailService;
import com.cineverse.payments.PaymentOrder;
import com.cineverse.shared.*;
import com.cineverse.users.*;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.*;
import java.time.*;
import java.util.*;

@Service
public class BookingService {
    private final JdbcTemplate db;
    private final UserRepository users;
    private final Audit audit;
    private final EmailService email;
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    public BookingService(JdbcTemplate db, UserRepository users, Audit audit, EmailService email) {
        this.db = db;
        this.users = users;
        this.audit = audit;
        this.email = email;
    }

    private static final String SHOW_SELECT =
            "select s.*,m.title as movie_title,m.slug as"
                + " movie_slug,m.poster,m.duration,m.age_rating,a.name as"
                + " auditorium_name,a.format,c.id as cinema_id,c.name as"
                + " cinema_name,c.city,c.address,(select count(*) from seats se where"
                + " se.auditorium_id=a.id and se.active=true) as total_seats,(select count(*) from"
                + " seat_reservations r join bookings b on b.id=r.booking_id where"
                + " r.showtime_id=s.id and (r.status='BOOKED' or b.hold_until>?)) as reserved_seats"
                + " from showtimes s join movies m on m.id=s.movie_id join auditoriums a on"
                + " a.id=s.auditorium_id join cinemas c on c.id=a.cinema_id";
    private final RowMapper<ShowtimeView> showMapper =
            (r, n) ->
                    new ShowtimeView(
                            r.getLong("id"),
                            r.getLong("movie_id"),
                            r.getString("movie_title"),
                            r.getString("movie_slug"),
                            r.getString("poster"),
                            r.getInt("duration"),
                            r.getString("age_rating"),
                            r.getLong("auditorium_id"),
                            r.getString("auditorium_name"),
                            r.getString("format"),
                            r.getLong("cinema_id"),
                            r.getString("cinema_name"),
                            r.getString("city"),
                            r.getString("address"),
                            r.getTimestamp("starts_at").toInstant(),
                            r.getTimestamp("ends_at").toInstant(),
                            r.getLong("base_price"),
                            r.getLong("total_seats") - r.getLong("reserved_seats"),
                            r.getLong("total_seats"));

    public List<CinemaView> cinemas() {
        return db.query(
                "select * from cinemas where active=true order by city,name",
                (r, n) ->
                        new CinemaView(
                                r.getLong("id"),
                                r.getString("name"),
                                r.getString("city"),
                                r.getString("address"),
                                r.getBoolean("active")));
    }

    @Transactional(readOnly = true)
    public List<ShowtimeView> showtimes(Long movieId, Long cinemaId, LocalDate day) {
        LocalDate date = day == null ? LocalDate.now(ZONE) : day;
        List<Object> args = new ArrayList<>();
        args.add(Timestamp.from(Instant.now()));
        String where =
                " where s.active=true and a.active=true and c.active=true and m.deleted=false and"
                    + " m.status in ('PUBLISHED','COMPLETED') and s.starts_at>? and s.starts_at>=?"
                    + " and s.starts_at<?";
        args.add(Timestamp.from(Instant.now().plusSeconds(600)));
        args.add(Timestamp.from(date.atStartOfDay(ZONE).toInstant()));
        args.add(Timestamp.from(date.plusDays(1).atStartOfDay(ZONE).toInstant()));
        if (movieId != null) {
            where += " and s.movie_id=?";
            args.add(movieId);
        }
        if (cinemaId != null) {
            where += " and c.id=?";
            args.add(cinemaId);
        }
        return db.query(
                SHOW_SELECT + where + " order by c.id,s.starts_at limit 200",
                showMapper,
                args.toArray());
    }

    public ShowtimeView showtime(Long id) {
        return db
                .query(SHOW_SELECT + " where s.id=?", showMapper, Timestamp.from(Instant.now()), id)
                .stream()
                .findFirst()
                .orElseThrow(ApiException::missing);
    }

    @Transactional
    public SeatMap seats(Long id) {
        lockShowtime(id);
        expireForShowtime(id);
        ShowtimeView s = showtime(id);
        availableShowtime(id);
        var seats =
                db.query(
                        "select se.*,r.status as reservation_status from seats se left join"
                            + " seat_reservations r on r.seat_id=se.id and r.showtime_id=? where"
                            + " se.auditorium_id=? and se.active=true order by"
                            + " se.row_label,se.seat_number",
                        (r, n) ->
                                new SeatView(
                                        r.getLong("id"),
                                        r.getString("row_label"),
                                        r.getInt("seat_number"),
                                        r.getString("kind"),
                                        s.basePrice() + surcharge(r.getString("kind")),
                                        r.getString("reservation_status") == null
                                                ? "AVAILABLE"
                                                : r.getString("reservation_status")),
                        id,
                        s.auditoriumId());
        return new SeatMap(s, seats, Instant.now());
    }

    @Transactional
    public BookingView hold(Long userId, HoldInput input) {
        lockShowtime(input.showtimeId());
        expireForShowtime(input.showtimeId());
        availableShowtime(input.showtimeId());
        ShowtimeView s = showtime(input.showtimeId());
        users.lockById(userId).orElseThrow(ApiException::missing);
        if (db.queryForObject(
                        "select count(*) from bookings where user_id=? and status in"
                                + " ('HELD','PENDING') and hold_until>?",
                        Long.class,
                        userId,
                        Timestamp.from(Instant.now()))
                >= 3)
            throw ApiException.bad("Bạn đã có 3 đơn đang giữ ghế. Hãy hoàn tất hoặc hủy đơn cũ.");
        List<Long> ids = input.seatIds().stream().distinct().sorted().toList();
        if (ids.isEmpty() || ids.size() > 8 || ids.size() != input.seatIds().size())
            throw ApiException.bad("Chọn từ 1 đến 8 ghế khác nhau.");
        List<ItemView> items = new ArrayList<>();
        long total = 0;
        for (Long seatId : ids) {
            var seat =
                    db.queryForList(
                            "select * from seats where id=? and auditorium_id=? and active=true for"
                                    + " update",
                            seatId,
                            s.auditoriumId());
            if (seat.isEmpty()) throw ApiException.bad("Ghế không thuộc phòng chiếu này.");
            if (db.queryForObject(
                            "select count(*) from seat_reservations where showtime_id=? and"
                                    + " seat_id=?",
                            Long.class,
                            s.id(),
                            seatId)
                    > 0)
                throw new ApiException(
                        HttpStatus.CONFLICT,
                        "SEAT_UNAVAILABLE",
                        "Một ghế vừa được người khác giữ. Vui lòng chọn lại.");
            var row = seat.getFirst();
            String kind = (String) row.get("kind"),
                    label = row.get("row_label") + String.valueOf(row.get("seat_number"));
            long price = s.basePrice() + surcharge(kind);
            items.add(new ItemView(seatId, label, kind, price));
            total = Math.addExact(total, price);
        }
        String id = "CVB-" + UUID.randomUUID();
        Instant now = Instant.now(), until = now.plusSeconds(480);
        db.update(
                "insert into"
                    + " bookings(id,user_id,showtime_id,status,total_amount,hold_until,created_at)"
                    + " values(?,?,?,'HELD',?,?,?)",
                id,
                userId,
                s.id(),
                total,
                Timestamp.from(until),
                Timestamp.from(now));
        for (var item : items) {
            db.update(
                    "insert into booking_items(booking_id,seat_id,seat_label,seat_kind,price)"
                            + " values(?,?,?,?,?)",
                    id,
                    item.seatId(),
                    item.seatLabel(),
                    item.seatKind(),
                    item.price());
            db.update(
                    "insert into seat_reservations(showtime_id,seat_id,booking_id,status)"
                            + " values(?,?,?,'HELD')",
                    s.id(),
                    item.seatId(),
                    id);
        }
        audit.log(
                userId,
                "BOOKING_HOLD",
                "BOOKING",
                id,
                String.join(",", items.stream().map(ItemView::seatLabel).toList()));
        return get(userId, id, false);
    }

    @Transactional
    public BookingView prepareCheckout(Long userId, String id) {
        var basic = bookingRow(id);
        lockShowtime(((Number) basic.get("showtime_id")).longValue());
        expireForShowtime(((Number) basic.get("showtime_id")).longValue());
        var booking = get(userId, id, false);
        if (!Set.of("HELD", "PENDING").contains(booking.status())
                || booking.holdUntil().isBefore(Instant.now()))
            throw ApiException.bad("Thời gian giữ ghế đã kết thúc. Vui lòng đặt lại.");
        availableShowtime(booking.showtime().id());
        db.update("update bookings set status='PENDING' where id=?", id);
        return booking;
    }

    @Transactional
    public BookingView get(Long userId, String id, boolean admin) {
        var b = bookingRow(id);
        if (!admin && !Objects.equals(((Number) b.get("user_id")).longValue(), userId))
            throw ApiException.forbidden();
        Long showtimeId = ((Number) b.get("showtime_id")).longValue();
        var items =
                db.query(
                        "select * from booking_items where booking_id=? order by seat_label",
                        (r, n) ->
                                new ItemView(
                                        r.getLong("seat_id"),
                                        r.getString("seat_label"),
                                        r.getString("seat_kind"),
                                        r.getLong("price")),
                        id);
        var tickets =
                db.query(
                        "select t.*,bi.seat_label from tickets t join booking_items bi on"
                                + " bi.booking_id=t.booking_id and bi.seat_id=t.seat_id where"
                                + " t.booking_id=? order by bi.seat_label",
                        (r, n) ->
                                new TicketView(
                                        r.getString("id"),
                                        r.getLong("seat_id"),
                                        r.getString("seat_label"),
                                        r.getString("code"),
                                        r.getString("status"),
                                        r.getTimestamp("issued_at").toInstant(),
                                        r.getTimestamp("used_at") == null
                                                ? null
                                                : r.getTimestamp("used_at").toInstant()),
                        id);
        var payment =
                db.queryForList(
                        "select id from payment_orders where booking_id=?", String.class, id);
        String state = (String) b.get("status");
        Instant holdUntil = instant(b.get("hold_until"));
        if (Set.of("HELD", "PENDING").contains(state) && holdUntil.isBefore(Instant.now()))
            state = "EXPIRED";
        return new BookingView(
                id,
                ((Number) b.get("user_id")).longValue(),
                state,
                ((Number) b.get("total_amount")).longValue(),
                holdUntil,
                instant(b.get("created_at")),
                b.get("confirmed_at") == null ? null : instant(b.get("confirmed_at")),
                showtime(showtimeId),
                items,
                tickets,
                payment.isEmpty() ? null : payment.getFirst());
    }

    @Transactional(readOnly = true)
    public List<BookingView> mine(Long userId) {
        return db
                .queryForList(
                        "select id from bookings where user_id=? order by created_at desc limit"
                                + " 100",
                        String.class,
                        userId)
                .stream()
                .map(id -> get(userId, id, false))
                .toList();
    }

    @Transactional
    public void cancel(Long userId, String id) {
        var b = bookingRow(id);
        lockShowtime(((Number) b.get("showtime_id")).longValue());
        var view = get(userId, id, false);
        if (!Set.of("HELD", "PENDING", "EXPIRED").contains(view.status()))
            throw ApiException.bad("Không thể tự hủy vé đã thanh toán. Vui lòng liên hệ rạp.");
        db.update("delete from seat_reservations where booking_id=? and status='HELD'", id);
        db.update("update bookings set status='CANCELLED' where id=?", id);
        audit.log(userId, "BOOKING_CANCEL", "BOOKING", id, "Người dùng hủy giữ ghế");
    }

    public void paymentFailed(PaymentOrder order) {
        if (order.bookingId == null) return;
        var row = bookingRow(order.bookingId);
        lockShowtime(((Number) row.get("showtime_id")).longValue());
        db.update(
                "delete from seat_reservations where booking_id=? and status='HELD'",
                order.bookingId);
        db.update(
                "update bookings set status='CANCELLED' where id=? and status in"
                        + " ('HELD','PENDING')",
                order.bookingId);
    }

    public void fulfillPaid(PaymentOrder order) {
        var row = bookingRow(order.bookingId);
        Long showtimeId = ((Number) row.get("showtime_id")).longValue();
        lockShowtime(showtimeId);
        var booking = get(order.userId, order.bookingId, false);
        if (booking.status().equals("CONFIRMED")) return;
        long reserved =
                db.queryForObject(
                        "select count(*) from seat_reservations where booking_id=? and"
                                + " status='HELD'",
                        Long.class,
                        booking.id());
        User u = users.findById(order.userId).orElseThrow(ApiException::missing);
        if (!Set.of("HELD", "PENDING").contains(booking.status())
                || reserved != booking.items().size()
                || booking.holdUntil().isBefore(Instant.now())) {
            db.update("update bookings set status='PAYMENT_REVIEW' where id=?", booking.id());
            audit.notify(
                    u.id,
                    "PAYMENT_REVIEW",
                    "Thanh toán cần đối soát với rạp",
                    "Thanh toán đã được xác nhận nhưng giữ ghế đã hết hạn. Chúng tôi chưa phát hành"
                            + " vé. Vui lòng liên hệ rạp.",
                    "/ve-cua-toi/" + booking.id());
            email.queue(
                    "booking-review:" + order.id,
                    u.email,
                    "CineVerse | Thanh toán cần hỗ trợ",
                    "BOOKING_REVIEW",
                    "Đơn vé cần được hỗ trợ",
                    "Đơn "
                            + booking.id()
                            + " đã nhận xác nhận thanh toán sau khi giữ ghế kết thúc. Chưa có vé"
                            + " được phát hành; quản trị viên sẽ đối soát và hỗ trợ.",
                    null,
                    null);
            audit.log(
                    u.id,
                    "BOOKING_PAYMENT_REVIEW",
                    "BOOKING",
                    booking.id(),
                    "Late payment: no seats reassigned");
            return;
        }
        Instant now = Instant.now();
        db.update(
                "update bookings set status='CONFIRMED',confirmed_at=? where id=?",
                Timestamp.from(now),
                booking.id());
        db.update("update seat_reservations set status='BOOKED' where booking_id=?", booking.id());
        for (var item : booking.items())
            db.update(
                    "insert into tickets(id,booking_id,seat_id,code,status,issued_at)"
                            + " values(?,?,?,?,'ISSUED',?)",
                    "CVT-" + UUID.randomUUID(),
                    booking.id(),
                    item.seatId(),
                    Text.random(),
                    Timestamp.from(now));
        String labels =
                String.join(", ", booking.items().stream().map(ItemView::seatLabel).toList());
        var s = booking.showtime();
        String method =
                switch (order.provider) {
                    case "PAYOS" -> "payOS · Chuyển khoản ngân hàng";
                    case "VNPAY_SANDBOX" -> "VNPAY Sandbox · Giao dịch thử nghiệm";
                    case "MOMO_SANDBOX" -> "MoMo Sandbox · Giao dịch thử nghiệm";
                    default -> "SIMULATED TEST PAYMENT · Không thu tiền thật";
                };
        String detail =
                "Thanh toán thành công — vé đã được xác nhận.\n"
                        + "Mã đặt vé: "
                        + booking.id()
                        + "\n"
                        + "Mã thanh toán: "
                        + order.id
                        + "\n"
                        + "Phim: "
                        + s.movieTitle()
                        + "\n"
                        + "Rạp: "
                        + s.cinemaName()
                        + "\n"
                        + "Phòng: "
                        + s.auditoriumName()
                        + "\n"
                        + "Suất chiếu: "
                        + s.startsAt()
                                .atZone(ZONE)
                                .format(
                                        java.time.format.DateTimeFormatter.ofPattern(
                                                "HH:mm 'ngày' dd/MM/yyyy"))
                        + " (giờ Việt Nam)\n"
                        + "Ghế: "
                        + labels
                        + "\n"
                        + "Tổng tiền: "
                        + String.format(
                                java.util.Locale.forLanguageTag("vi-VN"),
                                "%,d",
                                booking.totalAmount())
                        + " VND\n"
                        + "Phương thức: "
                        + method
                        + "\n"
                        + "Mã giao dịch: "
                        + Objects.toString(order.providerTransaction, order.id)
                        + "\n"
                        + "Mở vé điện tử bên dưới để xem mã QR. Vui lòng đến trước giờ chiếu 15"
                        + " phút.";
        email.queue(
                "receipt:" + order.id,
                u.email,
                "CineVerse | Vé xem phim của bạn đã sẵn sàng",
                "TICKET_RECEIPT",
                "Hẹn gặp bạn tại rạp, " + u.name,
                detail,
                email.frontendUrl() + "/ve-cua-toi/" + booking.id(),
                "Xem vé điện tử");
        email.queue(
                "admin-payment:" + order.id,
                email.adminRecipient(),
                "CineVerse | Đơn vé được xác nhận",
                "ADMIN_BOOKING",
                booking.id(),
                detail,
                email.frontendUrl() + "/ve-cua-toi/" + booking.id(),
                "Xem vé điện tử");
        audit.notify(
                u.id,
                "TICKET",
                "Vé của bạn đã sẵn sàng",
                s.movieTitle() + " · Ghế " + labels,
                "/ve-cua-toi/" + booking.id());
        audit.log(u.id, "TICKETS_ISSUED", "BOOKING", booking.id(), labels);
    }

    @Transactional
    public TicketView checkIn(String code, Long actor) {
        var rows =
                db.queryForList(
                        "select t.*,bi.seat_label,s.starts_at from tickets t join booking_items bi"
                                + " on bi.booking_id=t.booking_id and bi.seat_id=t.seat_id join"
                                + " bookings b on b.id=t.booking_id join showtimes s on"
                                + " s.id=b.showtime_id where t.code=? for update",
                        code);
        if (rows.isEmpty()) throw ApiException.bad("Mã vé không hợp lệ.");
        var t = rows.getFirst();
        if (!"ISSUED".equals(t.get("status")))
            throw ApiException.bad("Vé đã được sử dụng hoặc bị hủy.");
        Instant start = instant(t.get("starts_at"));
        if (start.isBefore(Instant.now().minus(Duration.ofHours(6))))
            throw ApiException.bad("Suất chiếu đã kết thúc quá lâu.");
        Instant now = Instant.now();
        db.update(
                "update tickets set status='USED',used_at=? where id=? and status='ISSUED'",
                Timestamp.from(now),
                t.get("id"));
        audit.log(actor, "TICKET_CHECKIN", "TICKET", t.get("id"), "Ghế " + t.get("seat_label"));
        return new TicketView(
                (String) t.get("id"),
                ((Number) t.get("seat_id")).longValue(),
                (String) t.get("seat_label"),
                code,
                "USED",
                instant(t.get("issued_at")),
                now);
    }

    @Scheduled(fixedDelay = 30000, initialDelay = 30000)
    @Transactional
    public void expire() {
        for (Long id :
                db.queryForList(
                        "select distinct showtime_id from bookings where status in"
                                + " ('HELD','PENDING') and hold_until<?",
                        Long.class,
                        Timestamp.from(Instant.now()))) {
            lockShowtime(id);
            expireForShowtime(id);
        }
    }

    private void expireForShowtime(Long id) {
        db.update(
                "delete from seat_reservations where status='HELD' and booking_id in (select id"
                        + " from bookings where showtime_id=? and status in"
                        + " ('HELD','PENDING','CANCELLED','EXPIRED') and hold_until<?)",
                id,
                Timestamp.from(Instant.now()));
        db.update(
                "update bookings set status='EXPIRED' where showtime_id=? and status in"
                        + " ('HELD','PENDING') and hold_until<?",
                id,
                Timestamp.from(Instant.now()));
    }

    private void lockShowtime(Long id) {
        if (db.queryForList("select id from showtimes where id=? for update", Long.class, id)
                .isEmpty()) throw ApiException.missing();
    }

    private void availableShowtime(Long id) {
        Long count =
                db.queryForObject(
                        "select count(*) from showtimes s join auditoriums a on"
                            + " a.id=s.auditorium_id join cinemas c on c.id=a.cinema_id join movies"
                            + " m on m.id=s.movie_id where s.id=? and s.active=true and"
                            + " a.active=true and c.active=true and m.deleted=false and m.status in"
                            + " ('PUBLISHED','COMPLETED') and s.starts_at>?",
                        Long.class,
                        id,
                        Timestamp.from(Instant.now().plusSeconds(600)));
        if (count == 0) throw ApiException.bad("Suất chiếu đã đóng đặt vé hoặc chưa được công bố.");
    }

    private Map<String, Object> bookingRow(String id) {
        return db.queryForList("select * from bookings where id=?", id).stream()
                .findFirst()
                .orElseThrow(ApiException::missing);
    }

    private long surcharge(String kind) {
        return "VIP".equals(kind) ? 20000 : 0;
    }

    private static Instant instant(Object value) {
        if (value instanceof Timestamp t) return t.toInstant();
        if (value instanceof LocalDateTime t) return t.toInstant(ZoneOffset.UTC);
        if (value instanceof Instant t) return t;
        throw new IllegalStateException("Unsupported database timestamp type");
    }
}

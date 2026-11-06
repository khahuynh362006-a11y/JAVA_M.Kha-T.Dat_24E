package com.cineverse.payments;

import com.cineverse.email.EmailService;
import com.cineverse.shared.*;
import com.cineverse.users.*;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.*;
import java.util.*;

@Service
public class PaymentService {
    private final PaymentOrderRepository orders;
    private final SubscriptionPlanRepository plans;
    private final UserRepository users;
    private final MomoSandboxPaymentGateway momo;
    private final VnpaySandboxPaymentGateway vnpay;
    private final PayosPaymentGateway payos;
    private final Map<String, PaymentGateway> gateways = new HashMap<>();
    private final JdbcTemplate db;
    private final Audit audit;
    private final EmailService email;
    private final String mode;
    private final com.cineverse.booking.BookingService bookings;

    public record OrderView(
            String id,
            Long userId,
            Long planId,
            String planName,
            int durationDays,
            long amount,
            String currency,
            String provider,
            String status,
            String payUrl,
            String providerTransaction,
            Instant createdAt,
            Instant paidAt,
            Instant expiresAt,
            String failureReason,
            String purpose,
            String bookingId) {}

    public record PlanView(
            Long id,
            String name,
            long price,
            int durationDays,
            String description,
            boolean active,
            boolean recommended,
            int sortOrder) {}

    public PaymentService(
            PaymentOrderRepository orders,
            SubscriptionPlanRepository plans,
            UserRepository users,
            MomoSandboxPaymentGateway momo,
            VnpaySandboxPaymentGateway vnpay,
            PayosPaymentGateway payos,
            List<PaymentGateway> gateways,
            JdbcTemplate db,
            Audit audit,
            EmailService email,
            com.cineverse.booking.BookingService bookings,
            @Value("${app.payments.mode}") String mode,
            Environment env) {
        this.orders = orders;
        this.plans = plans;
        this.users = users;
        this.momo = momo;
        this.vnpay = vnpay;
        this.payos = payos;
        gateways.forEach(g -> this.gateways.put(g.provider(), g));
        this.db = db;
        this.audit = audit;
        this.email = email;
        this.mode = mode;
        this.bookings = bookings;
        if (mode.equals("simulation") && !env.matchesProfiles("dev", "test"))
            throw new IllegalStateException("Simulation is forbidden outside development/test");
        if (!env.matchesProfiles("dev", "test") && (!mode.equals("payos") || !payos.configured()))
            throw new IllegalStateException(
                    "Production requires explicitly enabled and configured payOS");
    }

    @Transactional
    public OrderView create(Long userId, Long planId) {
        var p =
                plans.findById(planId)
                        .filter(x -> x.active)
                        .orElseThrow(() -> ApiException.bad("Gói thành viên không khả dụng."));
        PaymentGateway gateway = gateways.get(provider());
        if (gateway == null)
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "PAYMENT_DISABLED",
                    "Thanh toán chưa được cấu hình.");
        PaymentOrder o = new PaymentOrder();
        o.id = "CV" + UUID.randomUUID().toString().replace("-", "");
        o.requestId = UUID.randomUUID().toString();
        o.userId = userId;
        o.planId = p.id;
        o.planName = p.name;
        o.durationDays = p.durationDays;
        o.amount = p.price;
        o.provider = gateway.provider();
        o.expiresAt = Instant.now().plusSeconds(1800);
        orders.saveAndFlush(o);
        // Network create failures retain an auditable order for reconciliation.
        try {
            o.payUrl = gateway.create(o);
        } catch (ApiException e) {
            o.failureReason = e.code + ": " + e.getMessage();
            event(o, "CREATE_UNCERTAIN", e.code);
            return view(o);
        }
        event(o, "CREATED", o.provider + "; amount=" + o.amount);
        return view(o);
    }

    @Transactional
    public void ipn(Map<String, Object> input) {
        momo.verify(input);
        applyIpn(input);
    }

    @Transactional
    public OrderView createForBooking(Long userId, String bookingId) {
        if (mode.equals("payos") && !payos.configured())
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "PAYOS_NOT_CONFIGURED",
                    "Thanh toán payOS chưa được kết nối. Vui lòng liên hệ quản trị viên.");
        if (mode.equals("vnpay") && !vnpay.configured())
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "VNPAY_NOT_CONFIGURED",
                    "VNPAY Sandbox cần TmnCode và HashSecret do VNPAY cấp. Chưa tạo giao dịch.");
        if (mode.equals("momo") && !momo.configured())
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "MOMO_NOT_CONFIGURED",
                    "MoMo Sandbox chưa sẵn sàng. Cần bộ khóa merchant thử nghiệm và URL IPN HTTPS"
                            + " công khai.");
        var b = bookings.prepareCheckout(userId, bookingId);
        var existing = orders.findByBookingId(bookingId);
        if (existing.isPresent()) {
            if (!existing.get().provider.equals(provider()))
                throw ApiException.bad(
                        "Đơn này dùng phương thức cũ. Hãy hủy giữ ghế và chọn lại suất để dùng cổng"
                                + " thanh toán hiện tại.");
            return view(existing.get());
        }
        PaymentGateway gateway = gateways.get(provider());
        if (gateway == null)
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "PAYMENT_DISABLED",
                    "Thanh toán chưa được cấu hình.");
        PaymentOrder o = new PaymentOrder();
        o.id = "CV" + UUID.randomUUID().toString().replace("-", "");
        o.requestId = UUID.randomUUID().toString();
        o.userId = userId;
        o.bookingId = bookingId;
        o.purpose = "BOOKING";
        o.planId = null;
        o.planName =
                b.showtime()
                        .movieTitle()
                        .substring(0, Math.min(100, b.showtime().movieTitle().length()));
        o.durationDays = 0;
        o.amount = b.totalAmount();
        o.provider = gateway.provider();
        o.expiresAt = b.holdUntil();
        orders.saveAndFlush(o);
        try {
            o.payUrl = gateway.create(o);
        } catch (ApiException e) {
            o.failureReason = e.code + ": " + e.getMessage();
            if (e.code.equals("MOMO_REJECTED") || o.provider.equals("VNPAY_SANDBOX")) {
                o.status = "FAILED";
                bookings.paymentFailed(o);
                event(o, "CREATE_REJECTED", e.code);
                return view(o);
            }
            event(o, "CREATE_UNCERTAIN", e.code);
            return view(o);
        }
        event(o, "BOOKING_PAYMENT_CREATED", o.bookingId + "; amount=" + o.amount);
        return view(o);
    }

    private void applyIpn(Map<String, Object> in) {
        PaymentOrder o =
                orders.lockById(Objects.toString(in.get("orderId"), ""))
                        .orElseThrow(ApiException::missing);
        long amount;
        try {
            amount = Long.parseLong(Objects.toString(in.get("amount")));
        } catch (Exception e) {
            throw ApiException.bad("Số tiền không hợp lệ.");
        }
        if (!o.provider.equals("MOMO_SANDBOX")
                || !o.requestId.equals(in.get("requestId"))
                || o.amount != amount
                || !MomoSandboxPaymentGateway.info(o).equals(in.get("orderInfo")))
            throw ApiException.bad("Thông tin giao dịch không khớp đơn hàng.");
        int result = MomoSandboxPaymentGateway.code(in);
        String trans = Objects.toString(in.get("transId"), "");
        if (result == 0) {
            if (!trans.matches("[0-9]+") || trans.equals("0"))
                throw ApiException.bad("Mã giao dịch không hợp lệ.");
            fulfill(o, "MOMO:" + trans);
        } else if (result == 9000 || result == 1000 || result == 7000 || result == 7002) {
            event(o, "PENDING", "resultCode=" + result);
        } else if (!o.status.equals("PAID")) {
            o.status = result == 1006 ? "CANCELLED" : "FAILED";
            o.failureReason = "MoMo resultCode=" + result;
            bookings.paymentFailed(o);
            event(o, "FAILED", o.failureReason);
        }
    }

    @Transactional
    public OrderView simulate(Long userId, String id, String outcome) {
        if (!mode.equals("simulation") || !gateways.containsKey("SIMULATION"))
            throw ApiException.forbidden();
        PaymentOrder o = orders.lockById(id).orElseThrow(ApiException::missing);
        if (!o.userId.equals(userId) || !o.provider.equals("SIMULATION"))
            throw ApiException.forbidden();
        if (o.status.equals("PAID")) return view(o);
        if (!o.status.equals("PENDING")) throw ApiException.bad("Đơn không còn chờ thanh toán.");
        if (o.expiresAt.isBefore(Instant.now())) {
            o.status = "EXPIRED";
            event(o, "EXPIRED", "Simulation order expired");
            return view(o);
        }
        switch (outcome) {
            case "success" -> fulfill(o, "SIM:" + o.id);
            case "failure" -> {
                o.status = "FAILED";
                o.failureReason = "Giao dịch mô phỏng thất bại";
                bookings.paymentFailed(o);
                event(o, "FAILED", o.failureReason);
            }
            case "cancel" -> {
                o.status = "CANCELLED";
                bookings.paymentFailed(o);
                event(o, "CANCELLED", "User cancelled simulation");
            }
            default -> throw ApiException.bad("Kết quả mô phỏng không hợp lệ.");
        }
        return view(o);
    }

    private void fulfill(PaymentOrder o, String transaction) {
        if (o.status.equals("PAID")) {
            if (!transaction.equals(o.providerTransaction))
                throw ApiException.bad("Mã giao dịch trùng đơn không khớp.");
            return;
        }
        if ("BOOKING".equals(o.purpose)) {
            o.status = "PAID";
            o.providerTransaction = transaction;
            o.paidAt = Instant.now();
            o.failureReason = null;
            orders.saveAndFlush(o);
            bookings.fulfillPaid(o);
            event(o, "TICKET_PAYMENT_CONFIRMED", o.bookingId);
            audit.log(
                    o.userId,
                    "PAYMENT_FULFILLED",
                    "ORDER",
                    o.id,
                    o.provider + " " + o.amount + " VND");
            return;
        }
        // Valid late success can settle a previously failed/pending order; PAID is terminal.
        User u = users.lockById(o.userId).orElseThrow(ApiException::missing);
        Instant now = Instant.now(),
                start = u.vipUntil != null && u.vipUntil.isAfter(now) ? u.vipUntil : now;
        Instant end = start.plus(Duration.ofDays(o.durationDays));
        db.update(
                "insert into subscription_history(user_id,order_id,start_at,end_at)"
                        + " values(?,?,?,?)",
                u.id,
                o.id,
                Timestamp.from(start),
                Timestamp.from(end));
        o.status = "PAID";
        o.providerTransaction = transaction;
        o.paidAt = now;
        o.failureReason = null;
        u.vipUntil = end;
        orders.saveAndFlush(o);
        users.saveAndFlush(u);
        event(o, "FULFILLED", "VIP until " + end);
        audit.notify(
                u.id,
                "VIP",
                "VIP đã sẵn sàng",
                o.planName
                        + " đã được kích hoạt. "
                        + (o.provider.equals("SIMULATION")
                                ? "Thanh toán mô phỏng, không thu tiền thật."
                                : "MoMo Sandbox thử nghiệm."),
                "/goi-thanh-vien");
        email.receipt(u, o);
        audit.log(u.id, "PAYMENT_FULFILLED", "ORDER", o.id, o.provider + " " + o.amount + " VND");
    }

    @Transactional(readOnly = true)
    public OrderView get(Long userId, String id, boolean admin) {
        var o = orders.findById(id).orElseThrow(ApiException::missing);
        if (!admin && !o.userId.equals(userId)) throw ApiException.forbidden();
        return view(o);
    }

    @Transactional(readOnly = true)
    public List<OrderView> mine(Long id) {
        return orders.findByUserIdOrderByCreatedAtDesc(id).stream()
                .filter(o -> "BOOKING".equals(o.purpose))
                .map(PaymentService::view)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PlanView> plans() {
        return plans.findAll().stream()
                .filter(p -> p.active)
                .sorted(Comparator.comparingInt(p -> p.sortOrder))
                .map(PaymentService::planView)
                .toList();
    }

    public void event(PaymentOrder o, String kind, String detail) {
        db.update(
                "insert into payment_events(order_id,kind,detail,created_at) values(?,?,?,?)",
                o.id,
                kind,
                detail,
                Timestamp.from(Instant.now()));
    }

    public static OrderView view(PaymentOrder o) {
        return new OrderView(
                o.id,
                o.userId,
                o.planId,
                o.planName,
                o.durationDays,
                o.amount,
                "VND",
                o.provider,
                o.status,
                o.payUrl,
                o.providerTransaction,
                o.createdAt,
                o.paidAt,
                o.expiresAt,
                o.failureReason,
                o.purpose,
                o.bookingId);
    }

    public static PlanView planView(SubscriptionPlan p) {
        return new PlanView(
                p.id,
                p.name,
                p.price,
                p.durationDays,
                p.description,
                p.active,
                p.recommended,
                p.sortOrder);
    }

    public String mode() {
        return mode;
    }

    public record Configuration(String mode, String provider, boolean ready, String message) {}

    private String provider() {
        return switch (mode) {
            case "momo" -> "MOMO_SANDBOX";
            case "vnpay" -> "VNPAY_SANDBOX";
            case "payos" -> "PAYOS";
            case "simulation" -> "SIMULATION";
            default -> "DISABLED";
        };
    }

    public Configuration configuration() {
        boolean ready =
                mode.equals("simulation")
                        || (mode.equals("momo") && momo.configured())
                        || (mode.equals("vnpay") && vnpay.configured())
                        || (mode.equals("payos") && payos.configured());
        String message =
                mode.equals("payos")
                        ? (ready
                                ? "payOS · Thanh toán chuyển khoản ngân hàng, sử dụng tiền thật."
                                : "Thanh toán payOS chưa được kết nối. Vui lòng liên hệ quản trị"
                                      + " viên.")
                        : mode.equals("vnpay")
                                ? (ready
                                        ? "VNPAY Sandbox · Thanh toán bằng thẻ và OTP Test."
                                        : "Chưa có TmnCode/HashSecret VNPAY Test. Quản trị viên cần"
                                              + " hoàn tất cấu hình.")
                                : mode.equals("simulation")
                                        ? "Mô phỏng local, không gọi MoMo và không thu tiền."
                                        : mode.equals("momo")
                                                ? (ready
                                                        ? "MoMo Sandbox · Chỉ dùng ví và ứng dụng"
                                                              + " thử nghiệm."
                                                        : "MoMo Sandbox chưa được cấu hình đầy đủ."
                                                              + " Vui lòng liên hệ quản trị viên.")
                                                : "Thanh toán chưa được bật.";
        return new Configuration(mode, provider(), ready, message);
    }

    @Transactional
    public void settleQuery(String orderId, Map<String, Object> result) {
        var o = orders.lockById(orderId).orElseThrow(ApiException::missing);
        if (!o.provider.equals("MOMO_SANDBOX") || o.status.equals("PAID")) return;
        int code = MomoSandboxPaymentGateway.code(result);
        event(o, "RECONCILE", "MoMo query resultCode=" + code);
        if (code == 0) {
            long amount;
            try {
                amount = Long.parseLong(Objects.toString(result.get("amount")));
            } catch (Exception e) {
                throw ApiException.bad("Số tiền đối soát không hợp lệ.");
            }
            if (amount != o.amount || !o.id.equals(result.get("orderId")))
                throw ApiException.bad("Đối soát không khớp đơn hàng.");
            String trans = Objects.toString(result.get("transId"), "");
            if (!trans.matches("[1-9][0-9]*"))
                throw ApiException.bad("Mã giao dịch đối soát không hợp lệ.");
            fulfill(o, "MOMO:" + trans);
        }
        // Unknown/timeouts remain pending. Do not expire a wallet payment locally.
    }

    @Transactional
    public Map<String, String> vnpayIpn(Map<String, String> input) {
        if (!vnpay.validNotification(input)) return ack("97", "Invalid checksum");
        var found = orders.lockById(input.getOrDefault("vnp_TxnRef", ""));
        if (found.isEmpty() || !"VNPAY_SANDBOX".equals(found.get().provider))
            return ack("01", "Order not found");
        var order = found.get();
        if (!vnpayAmountMatches(order, input)
                || !VnpaySandboxPaymentGateway.info(order).equals(input.get("vnp_OrderInfo")))
            return ack("04", "Invalid amount or order info");
        if (order.status.equals("PAID")) return ack("02", "Order already confirmed");
        applyVnpay(order, input);
        return ack("00", "Confirm success");
    }

    @Transactional
    public void settleVnpayQuery(String id, Map<String, String> result) {
        var order = orders.lockById(id).orElseThrow(ApiException::missing);
        if (!order.provider.equals("VNPAY_SANDBOX") || order.status.equals("PAID")) return;
        if (!id.equals(result.get("vnp_TxnRef"))
                || !vnpayAmountMatches(order, result)
                || !VnpaySandboxPaymentGateway.info(order).equals(result.get("vnp_OrderInfo")))
            throw ApiException.bad("Kết quả đối soát không khớp đơn hàng.");
        event(
                order,
                "VNPAY_QUERY",
                "response="
                        + result.get("vnp_ResponseCode")
                        + "; status="
                        + result.get("vnp_TransactionStatus"));
        if ("00".equals(result.get("vnp_ResponseCode"))) applyVnpay(order, result);
    }

    private void applyVnpay(PaymentOrder order, Map<String, String> result) {
        String response = result.getOrDefault("vnp_ResponseCode", ""),
                status = result.getOrDefault("vnp_TransactionStatus", "");
        if (response.equals("00") && status.equals("00")) {
            String transaction = result.getOrDefault("vnp_TransactionNo", "");
            if (!transaction.matches("[1-9][0-9]*"))
                throw ApiException.bad("Mã giao dịch VNPAY không hợp lệ.");
            fulfill(order, "VNPAY:" + transaction);
        } else if (!Set.of("01", "05", "06").contains(status)) {
            order.status = response.equals("24") ? "CANCELLED" : "FAILED";
            order.failureReason = "VNPAY response=" + response + "; status=" + status;
            bookings.paymentFailed(order);
            event(order, "VNPAY_FAILED", order.failureReason);
        }
    }

    private boolean vnpayAmountMatches(PaymentOrder order, Map<String, String> input) {
        return Long.toString(Math.multiplyExact(order.amount, 100)).equals(input.get("vnp_Amount"));
    }

    private Map<String, String> ack(String code, String message) {
        return Map.of("RspCode", code, "Message", message);
    }

    @Transactional
    public void settlePayos(String id, vn.payos.model.v2.paymentRequests.PaymentLink result) {
        var order = orders.lockById(id).orElseThrow(ApiException::missing);
        if (!order.provider.equals("PAYOS")) throw ApiException.bad("Đơn không dùng payOS.");
        if (order.status.equals("PAID")) return;
        if (!Objects.equals(result.getOrderCode(), payos.code(id))
                || !Objects.equals(result.getAmount(), order.amount)
                || result.getId() == null
                || result.getStatus() == null)
            throw ApiException.bad("Kết quả payOS không khớp đơn hàng.");
        String existing =
                db.queryForObject(
                        "select payment_link_id from payos_links where order_id=?",
                        String.class,
                        id);
        if (existing != null && !existing.equals(result.getId()))
            throw ApiException.bad("Link payOS không khớp.");
        db.update("update payos_links set payment_link_id=? where order_id=?", result.getId(), id);
        String status = result.getStatus().name();
        event(
                order,
                "PAYOS_QUERY",
                "status="
                        + status
                        + "; paid="
                        + Objects.toString(result.getAmountPaid(), "unknown"));
        if (status.equals("PAID")) {
            if (result.getAmountPaid() == null || result.getAmountPaid() < order.amount)
                throw ApiException.bad("Số tiền payOS nhận được chưa đủ.");
            fulfill(order, "PAYOS:" + result.getId());
        } else if (Set.of("CANCELLED", "EXPIRED", "FAILED").contains(status)) {
            order.status = status;
            order.failureReason = "payOS status=" + status;
            bookings.paymentFailed(order);
        }
    }
}

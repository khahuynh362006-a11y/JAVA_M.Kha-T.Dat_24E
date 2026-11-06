package com.cineverse.email;

import com.cineverse.payments.PaymentOrder;
import com.cineverse.shared.Text;
import com.cineverse.users.User;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

@Service
public class EmailService {
    public String adminRecipient() {
        return admin;
    }

    public String frontendUrl() {
        return frontend;
    }

    private final JdbcTemplate db;
    private final String frontend, admin, layout;

    public EmailService(
            JdbcTemplate db,
            @Value("${app.frontend-url}") String frontend,
            @Value("${app.admin-email}") String admin)
            throws java.io.IOException {
        this.db = db;
        this.frontend = frontend;
        this.admin = admin;
        this.layout =
                new ClassPathResource("email/layout.html")
                        .getContentAsString(StandardCharsets.UTF_8);
    }

    public void verification(User u, String token) {
        queue(
                "verify:" + Text.hash(token),
                u.email,
                "CineVerse | Xác thực tài khoản của bạn",
                "VERIFY",
                "Xin chào " + u.name,
                "Xác thực email để mở cánh cửa đến vũ trụ điện ảnh. Liên kết có hiệu lực 24 giờ.",
                frontend + "/xac-thuc-email?token=" + token,
                "Xác thực tài khoản");
    }

    public void welcome(User u) {
        queue(
                "welcome:" + u.id,
                u.email,
                "Chào mừng bạn đến với CineVerse",
                "WELCOME",
                "Chào mừng, " + u.name,
                "Tài khoản đã sẵn sàng. Khám phá phim, chọn suất chiếu và đặt vé trên CineVerse.",
                frontend + "/kham-pha",
                "Khám phá ngay");
    }

    public void reset(User u, String token) {
        queue(
                "reset:" + Text.hash(token),
                u.email,
                "CineVerse | Đặt lại mật khẩu",
                "RESET",
                "Đặt lại mật khẩu",
                "Liên kết đặt lại mật khẩu có hiệu lực 30 phút và chỉ dùng được một lần.",
                frontend + "/dat-lai-mat-khau?token=" + token,
                "Đặt lại mật khẩu");
    }

    public void passwordChanged(User u) {
        queue(
                "password:" + UUID.randomUUID(),
                u.email,
                "CineVerse | Mật khẩu đã thay đổi",
                "SECURITY",
                "Mật khẩu đã được thay đổi",
                "Các phiên đăng nhập cũ đã được thu hồi. Nếu không phải bạn, hãy liên hệ quản trị"
                        + " viên.",
                frontend + "/dang-nhap",
                "Đăng nhập");
    }

    public void emailChange(String recipient, String token) {
        queue(
                "email-change:" + Text.hash(token),
                recipient,
                "CineVerse | Xác nhận email mới",
                "EMAIL_CHANGE",
                "Xác nhận địa chỉ email mới",
                "Liên kết chỉ dùng được một lần và hết hạn sau 30 phút. Xác nhận sẽ thu hồi tất cả"
                        + " phiên đăng nhập.",
                frontend + "/xac-thuc-email?change=1&token=" + token,
                "Xác nhận email mới");
    }

    public void adminNewUser(User u) {
        queue(
                "admin-user:" + u.id,
                admin,
                "CineVerse | Tài khoản mới",
                "ADMIN_USER",
                "Có thành viên mới",
                u.name + " · " + u.email + " · ID " + u.id + " · Chưa xác thực · " + u.createdAt,
                frontend + "/admin/users",
                "Xem thành viên");
    }

    public void receipt(User u, PaymentOrder o) {
        String method =
                o.provider.equals("SIMULATION")
                        ? "SIMULATED TEST PAYMENT — không thu tiền thật"
                        : "MoMo Sandbox — giao dịch thử nghiệm";
        String body =
                "Đơn "
                        + o.id
                        + " · "
                        + o.planName
                        + " · "
                        + String.format("%,d", o.amount)
                        + " VND · "
                        + method
                        + " · VIP đến "
                        + u.vipUntil
                        + " (UTC).";
        queue(
                "receipt:" + o.id,
                u.email,
                "CineVerse | Thanh toán VIP thành công",
                "RECEIPT",
                "Cảm ơn bạn, " + u.name,
                body,
                frontend + "/lich-su-thanh-toan",
                "Xem giao dịch");
        queue(
                "admin-payment:" + o.id,
                admin,
                "CineVerse | Giao dịch VIP thử nghiệm thành công",
                "ADMIN_PAYMENT",
                "Giao dịch được xác nhận",
                body + " · Người dùng " + u.email + " · Mã nhà cung cấp " + o.providerTransaction,
                frontend + "/admin/payments",
                "Xem giao dịch");
    }

    public void queue(
            String key,
            String recipient,
            String subject,
            String template,
            String title,
            String body,
            String url,
            String label) {
        if (db.queryForObject(
                        "select count(*) from email_outbox where dedup_key=?", Long.class, key)
                > 0) return;
        String button =
                url == null
                        ? ""
                        : "<p style=\"padding:20px 0\"><a href=\""
                                + Text.escape(url)
                                + "\" style=\"display:inline-block;background:#e50938;color:#fff;text-decoration:none;padding:14px"
                                + " 24px;border-radius:8px\">"
                                + Text.escape(label)
                                + "</a></p><p style=\"font-size:11px;word-break:break-all\">"
                                + Text.escape(url)
                                + "</p>";
        String html =
                layout.replace("{{title}}", Text.escape(title))
                        .replace("{{body}}", Text.escape(body))
                        .replace("{{button}}", button);
        Timestamp now = Timestamp.from(Instant.now());
        db.update(
                "insert into"
                    + " email_outbox(dedup_key,recipient,subject,template,html_body,text_body,status,attempts,created_at,next_attempt)"
                    + " values(?,?,?,?,?,?,'PENDING',0,?,?)",
                key,
                recipient,
                subject,
                template,
                html,
                title + "\n\n" + body + "\n\n" + (url == null ? "" : url),
                now,
                now);
    }
}

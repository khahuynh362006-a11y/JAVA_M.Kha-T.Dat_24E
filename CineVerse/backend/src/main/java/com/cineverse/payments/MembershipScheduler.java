package com.cineverse.payments;

import com.cineverse.email.EmailService;
import com.cineverse.shared.Audit;
import com.cineverse.users.*;

import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.*;

@Component
public class MembershipScheduler {
    private final PaymentOrderRepository orders;
    private final PaymentService payments;
    private final MomoSandboxPaymentGateway momo;
    private final JdbcTemplate db;
    private final EmailService email;
    private final Audit audit;
    private final UserRepository users;
    private final TransactionTemplate tx;

    public MembershipScheduler(
            PaymentOrderRepository orders,
            PaymentService payments,
            MomoSandboxPaymentGateway momo,
            JdbcTemplate db,
            EmailService email,
            Audit audit,
            UserRepository users,
            PlatformTransactionManager manager) {
        this.orders = orders;
        this.payments = payments;
        this.momo = momo;
        this.db = db;
        this.email = email;
        this.audit = audit;
        this.users = users;
        this.tx = new TransactionTemplate(manager);
    }

    @Scheduled(fixedDelay = 120000, initialDelay = 120000)
    public void reconcile() {
        if (!payments.mode().equals("momo") || !momo.configured()) return;
        for (var o :
                orders.findTop20ByProviderAndStatusAndCreatedAtBeforeOrderByCreatedAtAsc(
                        "MOMO_SANDBOX", "PENDING", Instant.now().minusSeconds(120))) {
            try {
                var verified = momo.query(o);
                payments.settleQuery(o.id, verified);
            } catch (Exception e) {
                LoggerFactory.getLogger(getClass())
                        .warn(
                                "Reconciliation pending for order {} ({})",
                                o.id,
                                e.getClass().getSimpleName());
            }
        }
    }

    @Scheduled(fixedDelay = 3600000, initialDelay = 60000)
    public void expiryNotices() {
        var ids =
                db.queryForList(
                        "select id from users where locked=false and vip_until>? and vip_until<?",
                        Long.class,
                        Timestamp.from(Instant.now()),
                        Timestamp.from(Instant.now().plus(Duration.ofDays(3))));
        for (Long id : ids)
            tx.executeWithoutResult(
                    s -> {
                        User u = users.lockById(id).orElseThrow();
                        String key = "vip-expiry:" + id + ":" + u.vipUntil;
                        if (db.queryForObject(
                                        "select count(*) from email_outbox where dedup_key=?",
                                        Long.class,
                                        key)
                                == 0) {
                            email.queue(
                                    key,
                                    u.email,
                                    "CineVerse | VIP sắp hết hạn",
                                    "EXPIRY",
                                    "Câu chuyện vẫn còn tiếp diễn",
                                    "Đặc quyền VIP của bạn kết thúc vào "
                                            + u.vipUntil
                                            + " (UTC). Gia hạn chủ động để tiếp tục khám phá.",
                                    null,
                                    null);
                            audit.notify(
                                    id,
                                    "VIP_EXPIRING",
                                    "VIP sắp hết hạn",
                                    "Bạn có thể gia hạn trước khi hết hạn để giữ trọn trải nghiệm.",
                                    "/vip");
                        }
                    });
    }
}

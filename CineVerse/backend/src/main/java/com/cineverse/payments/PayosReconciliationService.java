package com.cineverse.payments;

import com.cineverse.shared.ApiException;

import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PayosReconciliationService {
    private final PaymentService payments;
    private final PaymentOrderRepository orders;
    private final PayosPaymentGateway gateway;
    private final ConcurrentHashMap<String, Long> attempts = new ConcurrentHashMap<>();

    public PayosReconciliationService(
            PaymentService payments, PaymentOrderRepository orders, PayosPaymentGateway gateway) {
        this.payments = payments;
        this.orders = orders;
        this.gateway = gateway;
    }

    public PaymentService.OrderView reconcile(Long userId, String id, boolean admin) {
        return reconcile(userId, id, admin, false);
    }

    private PaymentService.OrderView reconcile(
            Long userId, String id, boolean admin, boolean webhook) {
        var view = payments.get(userId, id, admin);
        if (!view.provider().equals("PAYOS") || view.status().equals("PAID")) return view;
        long now = System.currentTimeMillis();
        boolean[] allowed = {webhook};
        attempts.compute(
                id,
                (key, last) -> {
                    if (webhook || last == null || now - last >= 15000) {
                        allowed[0] = true;
                        return now;
                    }
                    return last;
                });
        if (!allowed[0]) return view;
        if (attempts.size() > 5000)
            attempts.entrySet().removeIf(entry -> now - entry.getValue() > 60000);
        var order = orders.findById(id).orElseThrow(ApiException::missing);
        var verified = gateway.query(order);
        payments.settlePayos(id, verified);
        return payments.get(userId, id, admin);
    }

    public void webhook(Map<String, Object> payload) {
        var data = gateway.verifyWebhook(payload);
        if (!"00".equals(data.getCode())
                || !"VND".equals(data.getCurrency())
                || data.getOrderCode() == null
                || data.getAmount() == null
                || data.getAmount() <= 0) return;
        String id = gateway.orderId(data.getOrderCode());
        if (id == null) return; // payOS sends a sample when confirming webhook.
        var order = orders.findById(id).orElseThrow(ApiException::missing);
        if (!order.provider.equals("PAYOS"))
            throw ApiException.bad("Webhook không thuộc đơn payOS.");
        // Query signed provider state: a partial transfer webhook must not issue a full ticket.
        reconcile(order.userId, id, true, true);
    }

    @Scheduled(fixedDelay = 30000, initialDelay = 30000)
    public void pending() {
        if (!payments.mode().equals("payos") || !gateway.configured()) return;
        for (var order :
                orders
                        .findTop20ByProviderAndStatusAndCreatedAtBeforeOrderByCreatedAtAsc(
                                "PAYOS", "PENDING", Instant.now().minusSeconds(20))
                        .stream()
                        .limit(3)
                        .toList()) {
            try {
                reconcile(order.userId, order.id, false);
            } catch (Exception ex) {
                LoggerFactory.getLogger(getClass())
                        .warn(
                                "payOS query pending for {} ({})",
                                order.id,
                                ex.getClass().getSimpleName());
            }
        }
    }
}

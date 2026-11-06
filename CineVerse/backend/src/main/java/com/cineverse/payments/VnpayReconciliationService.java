package com.cineverse.payments;

import com.cineverse.shared.ApiException;

import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class VnpayReconciliationService {
    private final PaymentService payments;
    private final PaymentOrderRepository orders;
    private final VnpaySandboxPaymentGateway gateway;
    private final ConcurrentHashMap<String, Long> attempts = new ConcurrentHashMap<>();

    public VnpayReconciliationService(
            PaymentService payments,
            PaymentOrderRepository orders,
            VnpaySandboxPaymentGateway gateway) {
        this.payments = payments;
        this.orders = orders;
        this.gateway = gateway;
    }

    public PaymentService.OrderView reconcile(Long userId, String id, boolean admin) {
        var order = payments.get(userId, id, admin);
        if (!order.provider().equals("VNPAY_SANDBOX") || !order.status().equals("PENDING"))
            return order;
        long now = System.currentTimeMillis();
        boolean[] allowed = {false};
        attempts.compute(
                id,
                (key, last) -> {
                    if (last == null || now - last >= 15000) {
                        allowed[0] = true;
                        return now;
                    }
                    return last;
                });
        if (!allowed[0]) return order;
        if (attempts.size() > 5000) attempts.entrySet().removeIf(e -> now - e.getValue() > 60000);
        var entity = orders.findById(id).orElseThrow(ApiException::missing);
        var verified = gateway.query(entity);
        payments.settleVnpayQuery(id, verified);
        return payments.get(userId, id, admin);
    }

    @Scheduled(fixedDelay = 30000, initialDelay = 30000)
    public void retryPending() {
        if (!payments.mode().equals("vnpay") || !gateway.configured()) return;
        for (var order :
                orders
                        .findTop20ByProviderAndStatusAndCreatedAtBeforeOrderByCreatedAtAsc(
                                "VNPAY_SANDBOX", "PENDING", Instant.now().minusSeconds(20))
                        .stream()
                        .limit(3)
                        .toList()) {
            try {
                reconcile(order.userId, order.id, false);
            } catch (Exception ex) {
                LoggerFactory.getLogger(getClass())
                        .warn(
                                "VNPAY reconciliation pending for {} ({})",
                                order.id,
                                ex.getClass().getSimpleName());
            }
        }
    }
}

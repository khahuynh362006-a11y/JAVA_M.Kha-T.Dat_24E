package com.cineverse.payments;

import com.cineverse.security.CurrentUser;
import com.cineverse.shared.ApiException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.*;

@RestController
@RequestMapping("/api/v1/payments")
public class VnpayController {
    private final PaymentService payments;
    private final VnpaySandboxPaymentGateway gateway;
    private final VnpayReconciliationService reconciliation;
    private final PayosReconciliationService payos;
    private final CurrentUser current;
    private final String frontend;

    public VnpayController(
            PaymentService payments,
            VnpaySandboxPaymentGateway gateway,
            VnpayReconciliationService reconciliation,
            PayosReconciliationService payos,
            CurrentUser current,
            @Value("${app.frontend-url}") String frontend) {
        this.payments = payments;
        this.gateway = gateway;
        this.reconciliation = reconciliation;
        this.payos = payos;
        this.current = current;
        this.frontend = frontend;
    }

    private Map<String, String> parameters(MultiValueMap<String, String> input) {
        Map<String, String> result = new HashMap<>();
        input.forEach(
                (key, values) -> {
                    if (!key.matches("vnp_[A-Za-z0-9]+") || values.size() != 1)
                        throw ApiException.bad("Tham số thanh toán không hợp lệ.");
                    result.put(key, values.getFirst());
                });
        return result;
    }

    @GetMapping("/vnpay/ipn")
    public Map<String, String> ipn(@RequestParam MultiValueMap<String, String> input) {
        Map<String, String> values;
        try {
            values = parameters(input);
        } catch (ApiException ex) {
            return Map.of("RspCode", "97", "Message", "Invalid parameters");
        }
        return payments.vnpayIpn(values);
    }

    @GetMapping("/vnpay/return")
    public ResponseEntity<Void> returned(@RequestParam MultiValueMap<String, String> input) {
        var values = parameters(input);
        if (!gateway.validNotification(values))
            throw ApiException.bad("Chữ ký kết quả VNPAY không hợp lệ.");
        // Return only directs the browser. Signed IPN or signed server query settles the order.
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(
                        URI.create(
                                frontend
                                        + "/thanh-toan/ket-qua?orderId="
                                        + VnpaySignature.encode(
                                                values.getOrDefault("vnp_TxnRef", ""))))
                .build();
    }

    @PostMapping("/{id}/reconcile")
    public PaymentService.OrderView reconcile(@PathVariable String id) {
        var user = current.get();
        if (payments.get(user.id, id, user.role.equals("ADMIN")).provider().equals("PAYOS"))
            return payos.reconcile(user.id, id, user.role.equals("ADMIN"));
        return reconciliation.reconcile(user.id, id, user.role.equals("ADMIN"));
    }
}

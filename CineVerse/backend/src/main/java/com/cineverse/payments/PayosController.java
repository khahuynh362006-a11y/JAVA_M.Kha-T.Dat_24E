package com.cineverse.payments;

import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/payments/payos")
public class PayosController {
    private final PayosReconciliationService reconciliation;

    public PayosController(PayosReconciliationService reconciliation) {
        this.reconciliation = reconciliation;
    }

    @PostMapping("/webhook")
    public Map<String, Boolean> webhook(@RequestBody Map<String, Object> payload) {
        reconciliation.webhook(payload);
        return Map.of("success", true);
    }
}

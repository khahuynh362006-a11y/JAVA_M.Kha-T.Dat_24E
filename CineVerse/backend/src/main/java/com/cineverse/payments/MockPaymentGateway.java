package com.cineverse.payments;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile({"dev", "test"})
public class MockPaymentGateway implements PaymentGateway {
    public String provider() {
        return "SIMULATION";
    }

    public String create(PaymentOrder order) {
        return "/thanh-toan/mo-phong/" + order.id;
    }
}

package com.cineverse.payments;

public interface PaymentGateway {
    String provider();

    String create(PaymentOrder order);
}

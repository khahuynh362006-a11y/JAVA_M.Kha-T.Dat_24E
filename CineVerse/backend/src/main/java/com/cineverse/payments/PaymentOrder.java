package com.cineverse.payments;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "payment_orders")
public class PaymentOrder {
    @Id public String id;
    public Long userId;
    public Long planId;
    public String purpose = "MEMBERSHIP_LEGACY";
    public String bookingId;
    public String planName;
    public int durationDays;
    public long amount;
    public String provider;
    public String status = "PENDING";

    @Column(unique = true)
    public String requestId;

    @Column(unique = true)
    public String providerTransaction;

    public String payUrl;
    public Instant createdAt = Instant.now();
    public Instant expiresAt;
    public Instant paidAt;
    public String failureReason;
    @Version public long version;
}

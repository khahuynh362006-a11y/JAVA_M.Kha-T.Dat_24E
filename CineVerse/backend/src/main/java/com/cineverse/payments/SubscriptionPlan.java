package com.cineverse.payments;

import jakarta.persistence.*;

import java.util.*;

@Entity
@Table(name = "subscription_plans")
public class SubscriptionPlan {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    public String name;
    public long price;
    public int durationDays;
    public String description;
    public boolean active = true;
    public boolean recommended;
    public int sortOrder;
}

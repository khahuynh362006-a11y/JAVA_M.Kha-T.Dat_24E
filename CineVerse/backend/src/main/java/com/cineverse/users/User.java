package com.cineverse.users;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, unique = true)
    public String email;

    public String passwordHash;
    public String name;
    public String role = "USER";
    public boolean verified;
    public boolean locked;
    public String avatar;
    public boolean marketing;
    public Instant vipUntil;
    public int sessionVersion;
    public Instant createdAt = Instant.now();
    public Instant lastLogin;
    @Version public long version;
}

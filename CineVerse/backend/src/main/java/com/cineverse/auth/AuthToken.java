package com.cineverse.auth;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "auth_tokens")
public class AuthToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    public Long userId;

    @Column(unique = true)
    public String tokenHash;

    public String kind;
    public Instant expiresAt;
    public boolean consumed;
    public Instant createdAt = Instant.now();
    public String family;
    public String targetEmail;
    @Version public long version;
}

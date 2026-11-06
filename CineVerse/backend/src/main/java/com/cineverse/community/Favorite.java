package com.cineverse.community;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "favorites")
public class Favorite {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    public Long userId;
    public Long movieId;
    public Instant createdAt = Instant.now();
}

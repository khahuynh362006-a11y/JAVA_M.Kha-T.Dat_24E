package com.cineverse.community;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "reviews")
public class Review {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    public Long userId;
    public Long movieId;
    public int stars;
    public String content;
    public boolean hidden;
    public Instant createdAt = Instant.now();
    public Instant updatedAt = Instant.now();
}

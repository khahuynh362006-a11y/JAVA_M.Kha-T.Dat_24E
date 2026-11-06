package com.cineverse.community;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "watch_history")
public class WatchHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    public Long userId;
    public Long movieId;
    public Long episodeId;
    public double positionSeconds;
    public double durationSeconds;
    public boolean completed;
    public Instant updatedAt = Instant.now();
}

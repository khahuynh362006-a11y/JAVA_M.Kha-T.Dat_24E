package com.khadat.cinema.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DatabaseProbeRepository {
    private final JdbcTemplate jdbc;

    public DatabaseProbeRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void checkConnection() {
        // Fixed, read-only query against the actual schema; never client-supplied SQL.
        Long result = jdbc.queryForObject("SELECT COUNT(*) FROM roles", Long.class);
        if (result == null) {
            throw new IllegalStateException("Database probe returned no result");
        }
    }
}

package com.khadat.cinema.service;

import com.khadat.cinema.dto.HealthResponse;
import com.khadat.cinema.exception.DatabaseUnavailableException;
import com.khadat.cinema.repository.DatabaseProbeRepository;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

@Service
public class HealthService {
    private final DatabaseProbeRepository database;

    public HealthService(DatabaseProbeRepository database) {
        this.database = database;
    }

    public HealthResponse check() {
        try {
            database.checkConnection();
            return new HealthResponse("UP", "UP");
        } catch (DataAccessException ex) {
            throw new DatabaseUnavailableException();
        }
    }
}

package com.khadat.cinema.controller;

import com.khadat.cinema.dto.HealthResponse;
import com.khadat.cinema.service.HealthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
public class HealthController {
    private final HealthService health;

    public HealthController(HealthService health) {
        this.health = health;
    }

    @GetMapping
    public HealthResponse check() {
        return health.check();
    }
}

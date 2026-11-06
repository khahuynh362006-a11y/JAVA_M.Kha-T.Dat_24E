package com.cineverse.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;

import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class RateLimitFilter extends OncePerRequestFilter {
    private record Bucket(long minute, AtomicInteger count) {}

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    protected void doFilterInternal(
            HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        if (req.getMethod().equals("POST") && req.getRequestURI().startsWith("/api/v1/auth/")) {
            long minute = System.currentTimeMillis() / 60000;
            String key = req.getRemoteAddr() + ":" + req.getRequestURI();
            var b =
                    buckets.compute(
                            key,
                            (k, v) ->
                                    v == null || v.minute != minute
                                            ? new Bucket(minute, new AtomicInteger())
                                            : v);
            // Reloading protected pages rotates a refresh token. Give this endpoint
            // its own budget while keeping credential/token-entry attempts strict.
            int limit = req.getRequestURI().equals("/api/v1/auth/refresh") ? 120 : 20;
            if (b.count.incrementAndGet() > limit) {
                res.setHeader("Retry-After", "60");
                SecurityConfig.jsonError(
                        res, 429, "RATE_LIMIT", "Bạn thao tác quá nhanh. Vui lòng chờ một phút.");
                return;
            }
            if (buckets.size() > 5000)
                buckets.entrySet().removeIf(e -> e.getValue().minute < minute);
        }
        chain.doFilter(req, res);
    }
}

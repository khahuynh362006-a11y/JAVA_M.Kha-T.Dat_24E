package com.cineverse;

import static org.junit.jupiter.api.Assertions.*;

import com.cineverse.security.RateLimitFilter;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RateLimitFilterTest {
    private int request(RateLimitFilter filter, String path) throws Exception {
        var request = new MockHttpServletRequest("POST", path);
        request.setRemoteAddr("127.0.0.1");
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> {});
        return response.getStatus();
    }

    @Test
    void refreshBudgetSupportsReloadsAndStillLimitsAbuse() throws Exception {
        var filter = new RateLimitFilter();
        for (int i = 0; i < 120; i++) assertEquals(200, request(filter, "/api/v1/auth/refresh"));
        assertEquals(429, request(filter, "/api/v1/auth/refresh"));
        assertEquals(200, request(filter, "/api/v1/auth/login"));
    }

    @Test
    void credentialAttemptsKeepTheirStrictBudget() throws Exception {
        var filter = new RateLimitFilter();
        for (int i = 0; i < 20; i++) assertEquals(200, request(filter, "/api/v1/auth/login"));
        assertEquals(429, request(filter, "/api/v1/auth/login"));
        assertEquals(200, request(filter, "/api/v1/auth/refresh"));
    }
}

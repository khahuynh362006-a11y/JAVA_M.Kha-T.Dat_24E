package com.cineverse.security;

import com.cineverse.users.UserRepository;

import jakarta.servlet.*;
import jakarta.servlet.http.*;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class SessionGuardFilter extends OncePerRequestFilter {
    private final UserRepository users;

    public SessionGuardFilter(UserRepository users) {
        this.users = users;
    }

    protected void doFilterInternal(
            HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken token) {
            try {
                var u = users.findById(Long.valueOf(token.getName())).orElse(null);
                if (u == null
                        || u.locked
                        || u.sessionVersion
                                != ((Number) token.getToken().getClaim("sv")).intValue()) {
                    SecurityContextHolder.clearContext();
                    SecurityConfig.jsonError(
                            res, 401, "SESSION_EXPIRED", "Phiên đăng nhập đã hết hạn.");
                    return;
                }
            } catch (NumberFormatException e) {
                SecurityConfig.jsonError(res, 401, "INVALID_TOKEN", "Phiên không hợp lệ.");
                return;
            }
        }
        chain.doFilter(req, res);
    }
}

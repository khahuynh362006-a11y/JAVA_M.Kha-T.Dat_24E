package com.cineverse.security;

import com.cineverse.users.UserRepository;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

import jakarta.servlet.http.*;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.*;

import java.nio.charset.StandardCharsets;
import java.util.*;

import javax.crypto.spec.SecretKeySpec;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    PasswordEncoder passwords() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    byte[] jwtKey(@Value("${app.jwt-secret}") String secret, Environment env) {
        if (secret.length() < 32) {
            if (!env.matchesProfiles("dev", "test"))
                throw new IllegalStateException("JWT_SECRET must contain at least 32 characters");
            return com.cineverse.shared.Text.random().getBytes(StandardCharsets.UTF_8);
        }
        return secret.getBytes(StandardCharsets.UTF_8);
    }

    @Bean
    JwtEncoder jwtEncoder(byte[] jwtKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtKey));
    }

    @Bean
    JwtDecoder jwtDecoder(byte[] jwtKey) {
        var d =
                NimbusJwtDecoder.withSecretKey(new SecretKeySpec(jwtKey, "HmacSHA256"))
                        .macAlgorithm(MacAlgorithm.HS256)
                        .build();
        d.setJwtValidator(JwtValidators.createDefaultWithIssuer("cineverse"));
        return d;
    }

    @Bean
    SecurityFilterChain security(
            HttpSecurity http,
            UserRepository users,
            @Value("${app.frontend-url}") String origin,
            @Value("${app.admin-url:http://localhost:5174}") String adminOrigin)
            throws Exception {
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(
                jwt -> List.of(new SimpleGrantedAuthority("ROLE_" + jwt.getClaimAsString("role"))));
        http.csrf(c -> c.disable())
                .cors(c -> c.configurationSource(cors(origin, adminOrigin)))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(
                        a ->
                                a.dispatcherTypeMatchers(
                                                jakarta.servlet.DispatcherType.ASYNC,
                                                jakarta.servlet.DispatcherType.ERROR)
                                        .permitAll()
                                        .requestMatchers(
                                                "/api/v1/auth/**",
                                                "/api/v1/payments/momo/ipn",
                                                "/api/v1/payments/vnpay/ipn",
                                                "/api/v1/payments/vnpay/return",
                                                "/api/v1/payments/payos/webhook",
                                                "/actuator/health",
                                                "/v3/api-docs/**",
                                                "/swagger-ui/**",
                                                "/swagger-ui.html")
                                        .permitAll()
                                        .requestMatchers(
                                                HttpMethod.GET,
                                                "/api/v1/movies/**",
                                                "/api/v1/genres",
                                                "/api/v1/countries",
                                                "/api/v1/subscription-plans",
                                                "/api/v1/banners",
                                                "/api/v1/media/**",
                                                "/api/v1/assets/**",
                                                "/api/v1/settings",
                                                "/api/v1/cinemas",
                                                "/api/v1/showtimes/**",
                                                "/api/v1/payment-config")
                                        .permitAll()
                                        .requestMatchers("/api/v1/admin/**")
                                        .hasRole("ADMIN")
                                        .anyRequest()
                                        .authenticated())
                .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(converter)))
                .exceptionHandling(
                        e ->
                                e.authenticationEntryPoint(
                                                (req, res, ex) ->
                                                        jsonError(
                                                                res,
                                                                401,
                                                                "AUTH_REQUIRED",
                                                                "Vui lòng đăng nhập."))
                                        .accessDeniedHandler(
                                                (req, res, ex) ->
                                                        jsonError(
                                                                res,
                                                                403,
                                                                "FORBIDDEN",
                                                                "Bạn không có quyền truy cập.")))
                .headers(
                        h ->
                                h.withObjectPostProcessor(
                                                new org.springframework.security.config
                                                                .ObjectPostProcessor<
                                                        org.springframework.security.web.header
                                                                .HeaderWriterFilter>() {
                                                    @Override
                                                    public <
                                                                    O extends
                                                                            org.springframework
                                                                                            .security
                                                                                            .web
                                                                                            .header
                                                                                            .HeaderWriterFilter>
                                                            O postProcess(O filter) {
                                                        // Set security headers on the request
                                                        // thread before async trailer streaming
                                                        // starts.
                                                        filter.setShouldWriteHeadersEagerly(true);
                                                        return filter;
                                                    }
                                                })
                                        .contentSecurityPolicy(
                                                c ->
                                                        c.policyDirectives(
                                                                "default-src 'none';"
                                                                    + " frame-ancestors 'none'"))
                                        .referrerPolicy(
                                                r ->
                                                        r.policy(
                                                                ReferrerPolicyHeaderWriter
                                                                        .ReferrerPolicy
                                                                        .NO_REFERRER)))
                .addFilterBefore(new RateLimitFilter(), AnonymousAuthenticationFilter.class)
                .addFilterAfter(
                        new SessionGuardFilter(users),
                        org.springframework.security.oauth2.server.resource.web.authentication
                                .BearerTokenAuthenticationFilter.class);
        return http.build();
    }

    private CorsConfigurationSource cors(String origin, String adminOrigin) {
        var c = new CorsConfiguration();
        c.setAllowedOrigins(List.of(origin, adminOrigin));
        c.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        c.setAllowedHeaders(List.of("Authorization", "Content-Type", "Range"));
        c.setExposedHeaders(List.of("Content-Range", "Accept-Ranges"));
        c.setAllowCredentials(true);
        var s = new UrlBasedCorsConfigurationSource();
        s.registerCorsConfiguration("/**", c);
        return s;
    }

    public static void jsonError(HttpServletResponse r, int status, String code, String message)
            throws java.io.IOException {
        r.setStatus(status);
        r.setContentType("application/json;charset=UTF-8");
        r.getWriter()
                .write(
                        "{\"status\":"
                                + status
                                + ",\"errorCode\":\""
                                + code
                                + "\",\"message\":\""
                                + message
                                + "\"}");
    }
}

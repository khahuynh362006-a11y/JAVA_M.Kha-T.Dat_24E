package com.khadat.cinema.config;

import com.khadat.cinema.service.AuthService;
import jakarta.servlet.DispatcherType;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.csrf.InvalidCsrfTokenException;
import org.springframework.security.web.csrf.MissingCsrfTokenException;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfig {
    @Bean
    AuthenticationManager authenticationManager(AuthService users, PasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }

    @Bean SecurityContextRepository securityContextRepository() { return new HttpSessionSecurityContextRepository(); }
    @Bean CsrfTokenRepository csrfTokenRepository() { return new HttpSessionCsrfTokenRepository(); }

    @Bean
    SessionAuthenticationStrategy sessionAuthenticationStrategy(CsrfTokenRepository tokens) {
        CsrfAuthenticationStrategy csrf = new CsrfAuthenticationStrategy(tokens);
        csrf.setRequestHandler(new XorCsrfTokenRequestAttributeHandler());
        return new CompositeSessionAuthenticationStrategy(List.of(new ChangeSessionIdAuthenticationStrategy(), csrf));
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityErrorWriter errors,
            SecurityContextRepository contexts, CsrfTokenRepository tokens,
            SessionAuthenticationStrategy sessions) throws Exception {
        http.formLogin(AbstractHttpConfigurer::disable).httpBasic(AbstractHttpConfigurer::disable)
            .requestCache(cache -> cache.requestCache(new NullRequestCache()))
            .securityContext(context -> context.securityContextRepository(contexts).requireExplicitSave(true))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                .sessionAuthenticationStrategy(sessions))
            .csrf(csrf -> csrf.csrfTokenRepository(tokens))
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request,response,failure) -> errors.write(request,response,
                    HttpStatus.UNAUTHORIZED,"UNAUTHENTICATED","Cần đăng nhập."))
                .accessDeniedHandler((request,response,failure) -> {
                    boolean csrf = failure instanceof InvalidCsrfTokenException || failure instanceof MissingCsrfTokenException;
                    errors.write(request,response,HttpStatus.FORBIDDEN,csrf ? "CSRF_INVALID" : "FORBIDDEN",
                        csrf ? "CSRF token thiếu hoặc không hợp lệ." : "Không có quyền thực hiện.");
                }))
            .authorizeHttpRequests(auth -> auth
                .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                .requestMatchers(HttpMethod.GET,"/api/health","/api/auth/csrf","/","/index.html","/login.html","/assets/**").permitAll()
                .requestMatchers(HttpMethod.POST,"/api/auth/login").permitAll()
                .requestMatchers(HttpMethod.GET,"/api/auth/me").hasAnyRole("ADMIN","STAFF")
                .requestMatchers(HttpMethod.GET,"/api/admin/statistics/showtimes","/api/admin/statistics/booked-seats",
                    "/api/admin/statistics/occupancy","/api/admin/statistics/cancellations").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET,"/api/bookings/by-code/{code}").hasAnyRole("ADMIN","STAFF")
                .requestMatchers(HttpMethod.POST,"/api/bookings/{id}/cancel").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET,"/api/admin/showtimes","/api/admin/showtimes/{id}").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST,"/api/admin/showtimes/{id}/cancel").hasRole("ADMIN")
                // Exact patterns: movies/genres/rooms/seats implemented; other modules reserved.
                .requestMatchers(HttpMethod.GET,"/api/admin/rooms/{roomId}/seats").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST,"/api/admin/rooms/{roomId}/seats/bulk").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT,"/api/admin/rooms/{roomId}/seats/{seatId}").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE,"/api/admin/rooms/{roomId}/seats/{seatId}").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET,"/api/admin/movies","/api/admin/movies/{id}",
                    "/api/admin/genres","/api/admin/genres/{id}","/api/admin/rooms","/api/admin/rooms/{id}",
                    "/api/admin/seats","/api/admin/seats/{id}","/api/admin/users","/api/admin/users/{id}",
                    "/api/admin/statistics").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST,"/api/admin/movies","/api/admin/genres","/api/admin/rooms",
                    "/api/admin/seats","/api/admin/users","/api/admin/showtimes","/api/admin/rooms/{id}/seats").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT,"/api/admin/movies/{id}","/api/admin/genres/{id}","/api/admin/rooms/{id}",
                    "/api/admin/seats/{id}","/api/admin/users/{id}","/api/admin/showtimes/{id}").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE,"/api/admin/movies/{id}","/api/admin/genres/{id}","/api/admin/rooms/{id}",
                    "/api/admin/seats/{id}","/api/admin/users/{id}","/api/admin/showtimes/{id}").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET,"/api/showtimes","/api/showtimes/{id}","/api/showtimes/{id}/seats",
                    "/api/bookings","/api/bookings/{id}").hasAnyRole("ADMIN","STAFF")
                .requestMatchers(HttpMethod.POST,"/api/bookings").hasAnyRole("ADMIN","STAFF")
                .requestMatchers(HttpMethod.GET,"/admin/showtimes.html","/admin/bookings.html").hasAnyRole("ADMIN","STAFF")
                .requestMatchers(HttpMethod.GET,"/admin/dashboard.html","/admin/movies.html","/admin/genres.html",
                    "/admin/rooms.html","/admin/seats.html","/admin/users.html","/admin/statistics.html").hasRole("ADMIN")
                // Deny undeclared routes. STAFF booking cancellation remains disabled.
                .anyRequest().denyAll())
            .logout(logout -> logout.logoutRequestMatcher(new AntPathRequestMatcher("/api/auth/logout","POST"))
                .invalidateHttpSession(true).clearAuthentication(true).deleteCookies("JSESSIONID")
                .logoutSuccessHandler((request,response,authentication) -> {
                    response.setStatus(200);response.setContentType("application/json");
                    response.getWriter().write("{\"status\":\"LOGGED_OUT\"}");
                }));
        return http.build();
    }
}

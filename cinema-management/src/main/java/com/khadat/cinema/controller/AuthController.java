package com.khadat.cinema.controller;

import com.khadat.cinema.config.CinemaPrincipal;
import com.khadat.cinema.dto.auth.CsrfResponse;
import com.khadat.cinema.dto.auth.CurrentUserResponse;
import com.khadat.cinema.dto.auth.LoginRequest;
import com.khadat.cinema.exception.GlobalExceptionHandler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class AuthController {
    private final AuthenticationManager manager;
    private final SessionAuthenticationStrategy sessions;
    private final SecurityContextRepository contexts;
    private final GlobalExceptionHandler errors;

    public AuthController(AuthenticationManager manager, SessionAuthenticationStrategy sessions,
                          SecurityContextRepository contexts, GlobalExceptionHandler errors) {
        this.manager = manager;this.sessions = sessions;this.contexts = contexts;this.errors = errors;
    }

    @GetMapping("/csrf")
    public CsrfResponse csrf(CsrfToken token) {
        return new CsrfResponse(token.getHeaderName(),token.getToken());
    }

    @PostMapping(value = "/login", consumes = "application/json")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest credentials,
                                   HttpServletRequest request, HttpServletResponse response) {
        if (credentials.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            return errors.response(HttpStatus.BAD_REQUEST,"VALIDATION_FAILED","Dữ liệu đầu vào không hợp lệ.",
                request.getRequestURI(),Map.of("password",List.of("Mật khẩu không được vượt 72 byte UTF-8.")));
        }
        try {
            Authentication authentication = manager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(credentials.email().trim(),credentials.password()));
            // Custom JSON controller must explicitly apply fixation/CSRF strategy and save context.
            sessions.onAuthentication(authentication,request,response);
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            contexts.saveContext(context,request,response);
            return ResponseEntity.ok(CurrentUserResponse.from((CinemaPrincipal) authentication.getPrincipal()));
        } catch (AuthenticationException failure) {
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            SecurityContextHolder.clearContext();
            contexts.saveContext(context,request,response);
            return errors.response(HttpStatus.UNAUTHORIZED,"INVALID_CREDENTIALS","Thông tin đăng nhập không hợp lệ.",
                request.getRequestURI(),Map.of());
        }
    }

    @GetMapping("/me")
    public CurrentUserResponse me(Authentication authentication) {
        return CurrentUserResponse.from((CinemaPrincipal)authentication.getPrincipal());
    }
}

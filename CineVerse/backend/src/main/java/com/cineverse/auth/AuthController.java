package com.cineverse.auth;

import static com.cineverse.auth.AuthDtos.*;

import com.cineverse.security.CurrentUser;
import com.cineverse.shared.ApiException;

import jakarta.servlet.http.*;
import jakarta.validation.Valid;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class AuthController {
    private final AuthService auth;
    private final CurrentUser current;
    private final String origin;
    private final String adminOrigin;
    private final boolean secure;

    public AuthController(
            AuthService auth,
            CurrentUser current,
            @Value("${app.frontend-url}") String origin,
            @Value("${app.admin-url:http://localhost:5174}") String adminOrigin,
            @Value("${app.secure-cookie}") boolean secure) {
        this.auth = auth;
        this.current = current;
        this.origin = origin;
        this.adminOrigin = adminOrigin;
        this.secure = secure;
    }

    @PostMapping("/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> register(@Valid @RequestBody Register in) {
        auth.register(in);
        return message("Đăng ký thành công. Bạn có thể đăng nhập ngay.");
    }

    @PostMapping("/auth/login")
    public Session login(
            @Valid @RequestBody Login in,
            @RequestHeader(name = "Origin", required = false) String requestOrigin,
            HttpServletResponse response) {
        if (requestOrigin != null) checkOrigin(requestOrigin);
        var result = auth.login(in);
        cookie(response, result.refresh(), in.remember() ? 30 : 7, requestOrigin);
        return result.session();
    }

    @PostMapping("/auth/refresh")
    public Session refresh(
            HttpServletRequest request,
            @RequestHeader(name = "Origin", required = false) String requestOrigin,
            HttpServletResponse response) {
        checkOrigin(requestOrigin);
        var result = auth.refresh(refreshCookie(request, requestOrigin));
        cookie(response, result.refresh(), 7, requestOrigin);
        return result.session();
    }

    @PostMapping("/auth/logout")
    public Map<String, String> logout(
            HttpServletRequest request,
            @RequestHeader(name = "Origin", required = false) String requestOrigin,
            HttpServletResponse response) {
        checkOrigin(requestOrigin);
        auth.logout(refreshCookie(request, requestOrigin));
        cookie(response, "", 0, requestOrigin);
        return message("Đã đăng xuất.");
    }

    @PostMapping("/auth/verify-email")
    public Map<String, String> verify(@Valid @RequestBody TokenInput in) {
        auth.verify(in.token());
        return message("Email đã được xác thực. Bạn có thể đăng nhập.");
    }

    @PostMapping("/auth/confirm-email-change")
    public Map<String, String> confirmEmail(@Valid @RequestBody TokenInput in) {
        auth.confirmEmailChange(in.token());
        return message("Email mới đã được xác thực. Vui lòng đăng nhập lại bằng địa chỉ mới.");
    }

    @PostMapping("/users/me/email-change")
    public Map<String, String> changeEmail(@Valid @RequestBody EmailChange in) {
        auth.requestEmailChange(current.id(), in);
        return message("Đã gửi liên kết xác nhận đến email mới.");
    }

    @PostMapping("/auth/resend-verification")
    public Map<String, String> resend(@Valid @RequestBody EmailInput in) {
        return message("Không cần xác thực email. Bạn có thể đăng nhập ngay.");
    }

    @PostMapping("/auth/forgot-password")
    public Map<String, String> forgot(@Valid @RequestBody EmailInput in) {
        auth.forgot(in.email());
        return message("Nếu tài khoản tồn tại, hướng dẫn đặt lại mật khẩu sẽ được gửi qua email.");
    }

    @PostMapping("/auth/reset-password")
    public Map<String, String> reset(@Valid @RequestBody Reset in) {
        auth.reset(in);
        return message("Đã đặt lại mật khẩu. Vui lòng đăng nhập lại.");
    }

    @GetMapping("/users/me")
    public UserView me() {
        return view(current.get());
    }

    @PatchMapping("/users/me")
    public UserView profile(@Valid @RequestBody Profile in) {
        return auth.profile(current.id(), in);
    }

    @PatchMapping("/users/me/password")
    public Map<String, String> password(@Valid @RequestBody PasswordChange in) {
        auth.change(current.get(), in);
        return message("Đã đổi mật khẩu và thu hồi các phiên đăng nhập.");
    }

    @PostMapping("/users/me/logout-all")
    public Map<String, String> logoutAll() {
        auth.logoutAll(current.id());
        return message("Đã thu hồi tất cả phiên.");
    }

    private void checkOrigin(String value) {
        if (!origin.equals(value) && !adminOrigin.equals(value)) throw ApiException.forbidden();
    }

    private String cookieName(String requestOrigin) {
        return adminOrigin.equals(requestOrigin) ? "cv_admin_refresh" : "cv_refresh";
    }

    private String refreshCookie(HttpServletRequest request, String requestOrigin) {
        if (request.getCookies() != null)
            for (var cookie : request.getCookies())
                if (cookie.getName().equals(cookieName(requestOrigin))) return cookie.getValue();
        return null;
    }

    private void cookie(HttpServletResponse res, String value, int days, String requestOrigin) {
        res.addHeader(
                HttpHeaders.SET_COOKIE,
                ResponseCookie.from(cookieName(requestOrigin), value)
                        .httpOnly(true)
                        .secure(secure)
                        .sameSite("Strict")
                        .path("/api/v1/auth")
                        .maxAge(Duration.ofDays(days))
                        .build()
                        .toString());
    }

    private Map<String, String> message(String text) {
        return Map.of("message", text);
    }
}

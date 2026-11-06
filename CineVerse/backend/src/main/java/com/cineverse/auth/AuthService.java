package com.cineverse.auth;

import static com.cineverse.auth.AuthDtos.*;

import com.cineverse.email.EmailService;
import com.cineverse.shared.*;
import com.cineverse.users.*;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

@Service
public class AuthService {
    private final UserRepository users;
    private final AuthTokenRepository tokens;
    private final PasswordEncoder passwords;
    private final JwtEncoder jwt;
    private final EmailService email;
    private final Audit audit;

    public AuthService(
            UserRepository users,
            AuthTokenRepository tokens,
            PasswordEncoder passwords,
            JwtEncoder jwt,
            EmailService email,
            Audit audit) {
        this.users = users;
        this.tokens = tokens;
        this.passwords = passwords;
        this.jwt = jwt;
        this.email = email;
        this.audit = audit;
    }

    @Transactional
    public void register(Register in) {
        String address = in.email().strip().toLowerCase(Locale.ROOT);
        if (users.findByEmail(address).isPresent())
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_EXISTS", "Email đã được sử dụng.");
        User u = new User();
        u.email = address;
        u.name = in.name().strip();
        u.passwordHash = passwords.encode(in.password());
        u.marketing = in.marketing();
        users.saveAndFlush(u);
        email.welcome(u);
        audit.notify(
                u.id,
                "WELCOME",
                "Chào mừng đến CineVerse",
                "Tài khoản đã sẵn sàng. Chọn phim và đặt vé ngay.",
                "/kham-pha");
        email.adminNewUser(u);
        audit.log(u.id, "REGISTER", "USER", u.id, "Đăng ký tài khoản");
    }

    @Transactional
    public SessionResult login(Login in) {
        User u = users.findByEmail(in.email().strip().toLowerCase(Locale.ROOT)).orElse(null);
        // Always run BCrypt to reduce user enumeration by response timing.
        String encoded =
                u == null
                        ? "$2a$12$MFBApOGG6YqvhWRr.n6Ae.QTfHjWJp8sFoQCYHXQLq1pCV7yLFtXy"
                        : u.passwordHash;
        if (!passwords.matches(in.password(), encoded) || u == null)
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED,
                    "INVALID_CREDENTIALS",
                    "Email hoặc mật khẩu không đúng.");
        if (u.locked) throw ApiException.forbidden();
        u.lastLogin = Instant.now();
        audit.log(u.id, "LOGIN", "USER", u.id, "Đăng nhập thành công");
        return issue(u, in.remember() ? 30 : 7, UUID.randomUUID().toString());
    }

    @Transactional(noRollbackFor = RefreshReplay.class)
    public SessionResult refresh(String raw) {
        if (raw == null) throw unauthorized();
        AuthToken t = tokens.findByTokenHash(Text.hash(raw)).orElseThrow(this::unauthorized);
        if (!t.kind.equals("REFRESH")) throw unauthorized();
        if (t.consumed) {
            users.lockById(t.userId)
                    .ifPresent(
                            u -> {
                                u.sessionVersion++;
                                tokens.revokeRefresh(u.id);
                            });
            throw new RefreshReplay();
        }
        if (t.expiresAt.isBefore(Instant.now())) throw unauthorized();
        User u = users.lockById(t.userId).orElseThrow(this::unauthorized);
        if (u.locked) throw unauthorized();
        t.consumed = true;
        tokens.saveAndFlush(t);
        return issue(u, 7, t.family);
    }

    @Transactional
    public void logout(String raw) {
        if (raw == null) return;
        tokens.findByTokenHash(Text.hash(raw))
                .ifPresent(
                        t -> {
                            t.consumed = true;
                        });
    }

    @Transactional
    public void verify(String raw) {
        AuthToken t = consume(raw, "VERIFY");
        User u = users.lockById(t.userId).orElseThrow(ApiException::missing);
        u.verified = true;
        email.welcome(u);
        audit.notify(
                u.id,
                "WELCOME",
                "Chào mừng đến CineVerse",
                "Vũ trụ điện ảnh của bạn đã sẵn sàng.",
                "/kham-pha");
    }

    @Transactional
    public void resend(String address) {
        users.findByEmail(address.strip().toLowerCase(Locale.ROOT))
                .filter(u -> !u.verified && !u.locked)
                .ifPresent(this::sendVerification);
    }

    @Transactional
    public void forgot(String address) {
        users.findByEmail(address.strip().toLowerCase(Locale.ROOT))
                .filter(u -> !u.locked)
                .ifPresent(
                        u -> {
                            String t = token(u.id, "RESET", Duration.ofMinutes(30), null);
                            email.reset(u, t);
                        });
    }

    @Transactional
    public void reset(Reset in) {
        AuthToken t = consume(in.token(), "RESET");
        User u = users.lockById(t.userId).orElseThrow(ApiException::missing);
        u.passwordHash = passwords.encode(in.password());
        revoke(u);
        email.passwordChanged(u);
    }

    @Transactional
    public void change(User current, PasswordChange in) {
        User u = users.lockById(current.id).orElseThrow(ApiException::missing);
        if (!passwords.matches(in.currentPassword(), u.passwordHash))
            throw ApiException.bad("Mật khẩu hiện tại không đúng.");
        u.passwordHash = passwords.encode(in.password());
        revoke(u);
        email.passwordChanged(u);
    }

    @Transactional
    public UserView profile(Long id, Profile p) {
        User u = users.lockById(id).orElseThrow(ApiException::missing);
        u.name = p.name().strip();
        u.marketing = p.marketing();
        return view(u);
    }

    @Transactional
    public void logoutAll(Long id) {
        revoke(users.lockById(id).orElseThrow(ApiException::missing));
    }

    @Transactional
    public void requestEmailChange(Long id, EmailChange in) {
        User u = users.lockById(id).orElseThrow(ApiException::missing);
        if (!passwords.matches(in.password(), u.passwordHash))
            throw ApiException.bad("Mật khẩu hiện tại không đúng.");
        String address = in.email().strip().toLowerCase(Locale.ROOT);
        if (users.findByEmail(address).isPresent())
            throw ApiException.bad("Email này đã được sử dụng.");
        String raw = token(u.id, "EMAIL_CHANGE", Duration.ofMinutes(30), null);
        tokens.findByTokenHash(Text.hash(raw)).orElseThrow().targetEmail = address;
        email.emailChange(address, raw);
    }

    @Transactional
    public void confirmEmailChange(String raw) {
        AuthToken t = consume(raw, "EMAIL_CHANGE");
        User u = users.lockById(t.userId).orElseThrow(ApiException::missing);
        if (t.targetEmail == null || users.findByEmail(t.targetEmail).isPresent())
            throw ApiException.bad("Email mới không còn khả dụng.");
        String previous = u.email;
        u.email = t.targetEmail;
        revoke(u);
        email.queue(
                "email-changed:" + t.id,
                previous,
                "CineVerse | Email tài khoản đã thay đổi",
                "SECURITY",
                "Email tài khoản đã được thay đổi",
                "Địa chỉ đăng nhập mới đã được xác nhận. Nếu không phải bạn, hãy liên hệ quản trị"
                        + " viên.",
                null,
                null);
        audit.log(u.id, "EMAIL_CHANGE", "USER", u.id, "Địa chỉ mới đã được xác thực");
    }

    private void revoke(User u) {
        u.sessionVersion++;
        tokens.revokeRefresh(u.id);
    }

    private void sendVerification(User u) {
        String t = token(u.id, "VERIFY", Duration.ofHours(24), null);
        email.verification(u, t);
    }

    private AuthToken consume(String raw, String kind) {
        AuthToken t =
                tokens.findByTokenHash(Text.hash(raw))
                        .orElseThrow(
                                () -> ApiException.bad("Liên kết không hợp lệ hoặc đã hết hạn."));
        if (!t.kind.equals(kind) || t.consumed || t.expiresAt.isBefore(Instant.now()))
            throw ApiException.bad("Liên kết không hợp lệ hoặc đã hết hạn.");
        t.consumed = true;
        return t;
    }

    private SessionResult issue(User u, int days, String family) {
        Instant now = Instant.now();
        var claims =
                JwtClaimsSet.builder()
                        .issuer("cineverse")
                        .subject(u.id.toString())
                        .issuedAt(now)
                        .expiresAt(now.plusSeconds(900))
                        .claim("role", u.role)
                        .claim("sv", u.sessionVersion)
                        .build();
        String access =
                jwt.encode(
                                JwtEncoderParameters.from(
                                        JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                        .getTokenValue();
        return new SessionResult(
                new Session(access, view(u)),
                token(u.id, "REFRESH", Duration.ofDays(days), family));
    }

    private String token(Long user, String kind, Duration ttl, String family) {
        String raw = Text.random();
        AuthToken t = new AuthToken();
        t.userId = user;
        t.tokenHash = Text.hash(raw);
        t.kind = kind;
        t.expiresAt = Instant.now().plus(ttl);
        t.family = family;
        tokens.save(t);
        return raw;
    }

    private ApiException unauthorized() {
        return new ApiException(
                HttpStatus.UNAUTHORIZED, "SESSION_EXPIRED", "Phiên đăng nhập đã hết hạn.");
    }

    public static class RefreshReplay extends ApiException {
        public RefreshReplay() {
            super(HttpStatus.UNAUTHORIZED, "REFRESH_REPLAY", "Phiên đăng nhập đã bị thu hồi.");
        }
    }
}

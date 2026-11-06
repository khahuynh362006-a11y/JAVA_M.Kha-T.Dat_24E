package com.cineverse.auth;

import jakarta.validation.constraints.*;

import java.time.Instant;

public final class AuthDtos {
    public static final String STRONG =
            "^(?=.*[a-z])(?=.*[A-Z])(?=.*[0-9])(?=.*[^A-Za-z0-9]).{10,72}$";

    public record Register(
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Email @Size(max = 254) String email,
            @NotNull
                    @Pattern(
                            regexp = STRONG,
                            message =
                                    "Mật khẩu cần 10–72 ký tự, chữ hoa, chữ thường, số và ký tự đặc"
                                            + " biệt.")
                    String password,
            @AssertTrue(message = "Bạn cần đồng ý điều khoản.") boolean terms,
            boolean marketing) {}

    public record Login(
            @NotBlank @Email String email,
            @NotBlank @Size(max = 72) String password,
            boolean remember) {}

    public record EmailInput(@NotBlank @Email String email) {}

    public record EmailChange(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 72) String password) {}

    public record TokenInput(@NotBlank @Size(max = 200) String token) {}

    public record Reset(
            @NotBlank String token, @NotNull @Pattern(regexp = STRONG) String password) {}

    public record PasswordChange(
            @NotBlank String currentPassword, @NotNull @Pattern(regexp = STRONG) String password) {}

    public record Profile(@NotBlank @Size(max = 100) String name, boolean marketing) {}

    public record UserView(
            Long id,
            String name,
            String email,
            String role,
            boolean verified,
            boolean locked,
            String avatar,
            boolean marketing,
            boolean vip,
            Instant vipUntil,
            Instant createdAt) {}

    public record Session(String accessToken, UserView user) {}

    public record SessionResult(Session session, String refresh) {}

    public static UserView view(com.cineverse.users.User u) {
        return new UserView(
                u.id,
                u.name,
                u.email,
                u.role,
                u.verified,
                u.locked,
                u.avatar,
                u.marketing,
                u.vipUntil != null && u.vipUntil.isAfter(Instant.now()),
                u.vipUntil,
                u.createdAt);
    }
}

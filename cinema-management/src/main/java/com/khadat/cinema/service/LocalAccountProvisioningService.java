package com.khadat.cinema.service;

import com.khadat.cinema.dto.auth.LoginRequest;
import com.khadat.cinema.entity.User;
import com.khadat.cinema.entity.UserRole;
import com.khadat.cinema.entity.UserRoleId;
import com.khadat.cinema.repository.RoleRepository;
import com.khadat.cinema.repository.UserRepository;
import com.khadat.cinema.repository.UserRoleRepository;
import jakarta.validation.Validator;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("local-accounts")
public class LocalAccountProvisioningService {
    private final UserRepository users;
    private final RoleRepository roles;
    private final UserRoleRepository links;
    private final PasswordEncoder encoder;
    private final Validator validator;

    public LocalAccountProvisioningService(UserRepository users, RoleRepository roles, UserRoleRepository links,
                                           PasswordEncoder encoder, Validator validator) {
        this.users=users;this.roles=roles;this.links=links;this.encoder=encoder;this.validator=validator;
    }

    @Transactional
    public void create(Environment env) {
        if (!"LOCAL_ONLY".equals(env.getProperty("LOCAL_ACCOUNT_CONFIRM"))) {
            throw new IllegalArgumentException("Explicit LOCAL_ACCOUNT_CONFIRM=LOCAL_ONLY is required");
        }
        String email = env.getRequiredProperty("LOCAL_ACCOUNT_EMAIL").trim().toLowerCase(Locale.ROOT);
        String username = env.getRequiredProperty("LOCAL_ACCOUNT_USERNAME").trim();
        String name = env.getRequiredProperty("LOCAL_ACCOUNT_NAME").trim();
        String password = env.getRequiredProperty("LOCAL_ACCOUNT_PASSWORD");
        String role = env.getRequiredProperty("LOCAL_ACCOUNT_ROLE");
        int bytes = password.getBytes(StandardCharsets.UTF_8).length;
        if (!validator.validate(new LoginRequest(email,password)).isEmpty() || bytes<12 || bytes>72
            || !username.matches("[A-Za-z0-9._-]{3,50}") || name.isBlank() || name.length()>100
            || !(role.equals("ADMIN") || role.equals("STAFF"))) {
            throw new IllegalArgumentException("Invalid local account fields; no account was changed");
        }
        if (users.existsByEmailIgnoreCase(email) || users.existsByUsernameIgnoreCase(username)) {
            throw new IllegalArgumentException("Account already exists; no account was changed");
        }
        Long roleId = roles.findByCode(role).orElseThrow(() ->
            new IllegalArgumentException("Required role missing; import the existing seed first")).getId();
        User user=new User();user.setEmail(email);user.setUsername(username);user.setDisplayName(name);
        user.setPasswordHash(encoder.encode(password));user.setStatus("ACTIVE");
        users.saveAndFlush(user);
        UserRole link=new UserRole();link.setId(new UserRoleId(user.getId(),roleId));links.saveAndFlush(link);
    }
}

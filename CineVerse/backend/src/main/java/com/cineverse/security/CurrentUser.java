package com.cineverse.security;

import com.cineverse.shared.ApiException;
import com.cineverse.users.*;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentUser {
    private final UserRepository users;

    public CurrentUser(UserRepository users) {
        this.users = users;
    }

    public User get() {
        var a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || a.getName().equals("anonymousUser"))
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "Vui lòng đăng nhập.");
        return users.findById(Long.valueOf(a.getName())).orElseThrow(ApiException::missing);
    }

    public Long id() {
        return get().id;
    }
}

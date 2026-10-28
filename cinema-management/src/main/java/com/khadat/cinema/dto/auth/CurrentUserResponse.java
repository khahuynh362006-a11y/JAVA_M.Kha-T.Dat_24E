package com.khadat.cinema.dto.auth;

import com.khadat.cinema.config.CinemaPrincipal;
import java.util.List;

public record CurrentUserResponse(Long id, String username, String displayName, String email, List<String> roles) {
    public static CurrentUserResponse from(CinemaPrincipal principal) {
        return new CurrentUserResponse(principal.getId(), principal.getUsername(), principal.getDisplayName(),
            principal.getEmail(), principal.getRoles());
    }
}

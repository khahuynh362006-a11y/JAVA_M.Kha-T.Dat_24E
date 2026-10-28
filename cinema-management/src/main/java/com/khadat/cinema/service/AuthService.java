package com.khadat.cinema.service;

import com.khadat.cinema.config.CinemaPrincipal;
import com.khadat.cinema.entity.User;
import com.khadat.cinema.repository.UserRepository;
import com.khadat.cinema.repository.UserRoleRepository;
import java.util.List;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService implements UserDetailsService {
    private final UserRepository users;
    private final UserRoleRepository roles;

    public AuthService(UserRepository users, UserRoleRepository roles) {
        this.users = users; this.roles = roles;
    }

    /** DaoAuthenticationProvider passes the JSON email as the login identifier. */
    @Override @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) {
        User user = users.findByEmailIgnoreCase(email.trim())
            .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
        List<String> codes = roles.findRoleCodes(user.getId());
        boolean enabled = "ACTIVE".equals(user.getStatus()) && user.getPasswordHash() != null
            && !codes.isEmpty() && codes.stream().allMatch(code -> code.equals("ADMIN") || code.equals("STAFF"));
        return new CinemaPrincipal(user.getId(), user.getUsername(), user.getEmail(), user.getDisplayName(),
            user.getPasswordHash(), enabled, codes);
    }
}

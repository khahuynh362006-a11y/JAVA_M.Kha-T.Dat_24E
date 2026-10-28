package com.khadat.cinema.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.io.Serial;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/** Session identity, not a JPA entity. Credentials are erased after authentication. */
public final class CinemaPrincipal implements UserDetails, CredentialsContainer {
    @Serial private static final long serialVersionUID = 1L;
    private final Long id;
    private final String username;
    private final String email;
    private final String displayName;
    private final List<String> roles;
    private final boolean enabled;
    private transient String passwordHash;

    public CinemaPrincipal(Long id, String username, String email, String displayName,
                           String passwordHash, boolean enabled, List<String> roles) {
        this.id = id; this.username = username; this.email = email; this.displayName = displayName;
        this.passwordHash = passwordHash; this.enabled = enabled; this.roles = List.copyOf(roles);
    }
    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getDisplayName() { return displayName; }
    public List<String> getRoles() { return roles; }
    @Override public String getUsername() { return username; }
    @JsonIgnore @Override public String getPassword() { return passwordHash; }
    @Override public boolean isEnabled() { return enabled; }
    @Override public Collection<? extends GrantedAuthority> getAuthorities() {
        return roles.stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role)).toList();
    }
    @Override public void eraseCredentials() { passwordHash = null; }
    @Override public String toString() { return "CinemaPrincipal[id=" + id + "]"; }
}

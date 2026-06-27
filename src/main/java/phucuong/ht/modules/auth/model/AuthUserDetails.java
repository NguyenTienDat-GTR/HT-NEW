package phucuong.ht.modules.auth.model;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Getter
public class AuthUserDetails implements UserDetails {

    private final String username;
    private final String password;
    private final String leaderId;
    private final List<String> roles;
    private final List<String> permissions;
    private final boolean enabled;
    private final List<GrantedAuthority> authorities;

    private AuthUserDetails(
            String username,
            String password,
            String leaderId,
            List<String> roles,
            List<String> permissions,
            boolean enabled
    ) {
        this.username = username;
        this.password = password;
        this.leaderId = leaderId;
        this.roles = List.copyOf(roles);
        this.permissions = List.copyOf(permissions);
        this.enabled = enabled;
        this.authorities = buildAuthorities(roles, permissions);
    }

    public static AuthUserDetails databasePrincipal(
            String username,
            String password,
            String leaderId,
            List<String> roles,
            List<String> permissions,
            boolean enabled
    ) {
        return new AuthUserDetails(username, password, leaderId, roles, permissions, enabled);
    }

    public static AuthUserDetails tokenPrincipal(
            String username,
            String leaderId,
            List<String> roles,
            List<String> permissions
    ) {
        return new AuthUserDetails(username, "", leaderId, roles, permissions, true);
    }

    private static List<GrantedAuthority> buildAuthorities(List<String> roles, List<String> permissions) {
        Set<String> authorities = new LinkedHashSet<>();
        roles.stream()
                .filter(role -> role != null && !role.isBlank())
                .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
                .forEach(authorities::add);
        permissions.stream()
                .filter(permission -> permission != null && !permission.isBlank())
                .forEach(authorities::add);
        return authorities.stream()
                .<GrantedAuthority>map(SimpleGrantedAuthority::new)
                .toList();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return enabled;
    }

    @Override
    public boolean isAccountNonLocked() {
        return enabled;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return enabled;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}

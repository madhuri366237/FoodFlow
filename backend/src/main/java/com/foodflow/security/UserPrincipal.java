package com.foodflow.security;

import com.foodflow.entity.Role;
import com.foodflow.entity.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Adapter between our User entity and Spring Security's UserDetails.
 *
 * <p>An immutable snapshot, not the JPA entity: it is stored in the SecurityContext for the
 * whole request, and a detached entity there would invite LazyInitializationExceptions and
 * accidental writes. Controllers receive it via {@code @AuthenticationPrincipal UserPrincipal me}.
 */
@Getter
public final class UserPrincipal implements UserDetails {

    private final Long id;
    private final String email;
    private final String passwordHash;
    private final Role role;
    private final boolean enabled;

    private UserPrincipal(Long id, String email, String passwordHash, Role role, boolean enabled) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.enabled = enabled;
    }

    public static UserPrincipal from(User user) {
        return new UserPrincipal(user.getId(), user.getEmail(), user.getPasswordHash(),
                user.getRole(), user.isEnabled());
    }

    /*
     * Spring Security's convention: an authority named "ROLE_X" satisfies hasRole("X").
     * So Role.RESTAURANT_OWNER becomes "ROLE_RESTAURANT_OWNER" and hasRole("RESTAURANT_OWNER") matches.
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    /** Spring Security's "username" is our email. */
    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public String toString() {
        return "UserPrincipal[id=" + id + ", role=" + role + "]"; // never include the hash
    }
}

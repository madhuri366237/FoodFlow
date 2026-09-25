package com.foodflow.security;

import com.foodflow.entity.User;
import com.foodflow.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loads users for Spring Security.
 * <ul>
 *   <li>By email: used by DaoAuthenticationProvider during login.</li>
 *   <li>By id: used by JwtFilter on every authenticated request (the token subject is the id).</li>
 * </ul>
 *
 * <p>Being a UserDetailsService bean also stops Spring Boot from creating its default
 * in-memory "user" account with a generated password.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserPrincipal loadUserByUsername(String email) {
        return userRepository.findByEmail(User.normalizeEmail(email))
                .map(UserPrincipal::from)
                // Spring converts this into BadCredentialsException, so the caller never
                // learns whether the email or the password was wrong.
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }

    public UserPrincipal loadUserById(Long id) {
        return userRepository.findById(id)
                .map(UserPrincipal::from)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }
}

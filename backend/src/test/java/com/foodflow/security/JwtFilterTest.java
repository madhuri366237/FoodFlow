package com.foodflow.security;

import com.foodflow.entity.Role;
import com.foodflow.entity.User;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import static com.foodflow.TestFixtures.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The filter in isolation: JwtUtil and the user lookup are mocks, so each case (valid, expired,
 * deleted user, disabled user, no header) is set up in one line, without real tokens or a database.
 * In every case the request continues down the chain; the filter only decides WHO the caller is.
 */
@ExtendWith(MockitoExtension.class)
class JwtFilterTest {

    @Mock private JwtUtil jwtUtil;
    @Mock private CustomUserDetailsService userDetailsService;
    @Mock private FilterChain chain;

    private JwtFilter filter;
    private MockHttpServletRequest request;
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @BeforeEach
    void setUp() {
        filter = new JwtFilter(jwtUtil, userDetailsService);
        request = new MockHttpServletRequest("GET", "/api/users/me");
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private Authentication authentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    @Test
    void validTokenAuthenticatesTheCurrentUserWithTheirRole() throws Exception {
        request.addHeader("Authorization", "Bearer good-token");
        when(jwtUtil.extractUserId("good-token")).thenReturn(42L);
        User owner = user(42L, Role.RESTAURANT_OWNER);
        when(userDetailsService.loadUserById(42L)).thenReturn(UserPrincipal.from(owner));

        filter.doFilter(request, response, chain);

        assertThat(authentication().getPrincipal()).isInstanceOf(UserPrincipal.class);
        assertThat(authentication().getAuthorities()).extracting("authority").containsExactly("ROLE_RESTAURANT_OWNER");
        verify(chain).doFilter(request, response);
    }

    @Test
    void noHeaderMeansAnonymousAndNoTokenWork() throws Exception {
        filter.doFilter(request, response, chain);

        assertThat(authentication()).isNull();
        verifyNoInteractions(jwtUtil, userDetailsService);
        verify(chain).doFilter(request, response);
    }

    @Test
    void nonBearerHeaderIsIgnored() throws Exception {
        request.addHeader("Authorization", "Basic dXNlcjpwYXNz");

        filter.doFilter(request, response, chain);

        assertThat(authentication()).isNull();
        verifyNoInteractions(jwtUtil);
    }

    @Test
    void expiredTokenLeavesTheRequestAnonymous() throws Exception {
        request.addHeader("Authorization", "Bearer old-token");
        when(jwtUtil.extractUserId("old-token")).thenThrow(new ExpiredJwtException(null, null, "expired"));

        filter.doFilter(request, response, chain);

        assertThat(authentication()).isNull();
        verify(chain).doFilter(request, response); // a protected endpoint will then answer 401
    }

    @Test
    void tokenOfDeletedUserIsRejected() throws Exception {
        request.addHeader("Authorization", "Bearer token");
        when(jwtUtil.extractUserId("token")).thenReturn(7L);
        when(userDetailsService.loadUserById(7L)).thenThrow(new UsernameNotFoundException("gone"));

        filter.doFilter(request, response, chain);

        assertThat(authentication()).isNull();
    }

    @Test
    void tokenOfDisabledUserIsRejectedEvenThoughItIsValid() throws Exception {
        request.addHeader("Authorization", "Bearer token");
        when(jwtUtil.extractUserId("token")).thenReturn(7L);
        User disabled = user(7L, Role.CUSTOMER);
        disabled.setEnabled(false);
        when(userDetailsService.loadUserById(any())).thenReturn(UserPrincipal.from(disabled));

        filter.doFilter(request, response, chain);

        assertThat(authentication()).isNull();
    }
}

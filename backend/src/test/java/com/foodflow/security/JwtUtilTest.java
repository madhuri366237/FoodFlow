package com.foodflow.security;

import com.foodflow.config.JwtProperties;
import com.foodflow.entity.Role;
import com.foodflow.entity.User;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.SignatureException;
import io.jsonwebtoken.security.WeakKeyException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Plain unit test: no Spring context, no database. */
class JwtUtilTest {

    private static final String SECRET = "np6ZOQL5Pjn4YIAxIdTy+GSP+sJUyuoU7M3weR47u0+ZuTMDldviqmswtHf93CXk";
    private static final String OTHER_SECRET =
            Base64.getEncoder().encodeToString("a-completely-different-key-of-48-bytes-length!!!".getBytes(StandardCharsets.UTF_8));
    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    private JwtUtil jwtUtil;
    private UserPrincipal principal;

    @BeforeEach
    void setUp() {
        jwtUtil = newJwtUtil(SECRET, Clock.fixed(NOW, ZoneOffset.UTC));
        User user = new User("Asha", "asha@example.com", "hash", null, Role.CUSTOMER);
        ReflectionTestUtils.setField(user, "id", 42L); // normally assigned by the database
        principal = UserPrincipal.from(user);
    }

    private static JwtUtil newJwtUtil(String secret, Clock clock) {
        return new JwtUtil(new JwtProperties(secret, Duration.ofHours(1).toMillis(), "foodflow"), clock);
    }

    @Test
    void generatedTokenRoundTripsToUserId() {
        String token = jwtUtil.generateToken(principal);

        assertThat(token.split("\\.")).hasSize(3); // header.payload.signature
        assertThat(jwtUtil.extractUserId(token)).isEqualTo(42L);
    }

    @Test
    void payloadIsReadableButContainsNoPassword() {
        String payload = new String(Base64.getUrlDecoder().decode(jwtUtil.generateToken(principal).split("\\.")[1]),
                StandardCharsets.UTF_8);

        assertThat(payload).contains("\"sub\":\"42\"", "\"role\":\"CUSTOMER\"").doesNotContain("hash");
    }

    @Test
    void tamperedPayloadIsRejected() {
        String[] parts = jwtUtil.generateToken(principal).split("\\.");
        String forgedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"sub\":\"1\",\"role\":\"ADMIN\",\"iss\":\"foodflow\"}".getBytes(StandardCharsets.UTF_8));
        String forged = parts[0] + "." + forgedPayload + "." + parts[2];

        assertThatThrownBy(() -> jwtUtil.extractUserId(forged)).isInstanceOf(SignatureException.class);
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        String foreignToken = newJwtUtil(OTHER_SECRET, Clock.fixed(NOW, ZoneOffset.UTC)).generateToken(principal);

        assertThatThrownBy(() -> jwtUtil.extractUserId(foreignToken)).isInstanceOf(SignatureException.class);
    }

    @Test
    void expiredTokenIsRejected() {
        String token = jwtUtil.generateToken(principal); // valid for 1 hour from NOW
        JwtUtil twoHoursLater = newJwtUtil(SECRET, Clock.fixed(NOW.plus(Duration.ofHours(2)), ZoneOffset.UTC));

        assertThatThrownBy(() -> twoHoursLater.extractUserId(token)).isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void garbageIsRejected() {
        assertThatThrownBy(() -> jwtUtil.extractUserId("not.a.jwt")).isInstanceOf(JwtException.class);
    }

    @Test
    void secretShorterThan256BitsFailsFastAtStartup() {
        String shortSecret = Base64.getEncoder().encodeToString("only-16-bytes!!!".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> newJwtUtil(shortSecret, Clock.systemUTC())).isInstanceOf(WeakKeyException.class);
    }
}

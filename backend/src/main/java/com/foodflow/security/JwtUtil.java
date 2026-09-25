package com.foodflow.security;

import com.foodflow.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;

/**
 * Creates and verifies JWTs.
 *
 * <p>A token has three Base64URL parts: header.payload.signature.
 * <ul>
 *   <li>header:    {"alg":"HS384"}</li>
 *   <li>payload:   {"sub":"42","email":"...","role":"CUSTOMER","iss":"foodflow","iat":...,"exp":...}</li>
 *   <li>signature: HMAC-SHA(header + "." + payload, secret key)</li>
 * </ul>
 * JJWT picks the HMAC strength from the key size: 32+ bytes gives HS256, 48+ gives HS384,
 * 64+ gives HS512. The recommended {@code openssl rand -base64 48} key therefore yields HS384.
 * The payload is only encoded, NOT encrypted: anyone can read it, so it never contains
 * secrets. What the signature guarantees is that nobody without the key can change it:
 * editing "role":"ADMIN" into the payload breaks the signature and the token is rejected.
 */
@Component
public class JwtUtil {

    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_ROLE = "role";

    private final SecretKey signingKey;
    private final JwtParser parser;
    private final JwtProperties properties;
    private final Clock clock;

    public JwtUtil(JwtProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        // hmacShaKeyFor throws WeakKeyException for keys under 256 bits, so a short
        // JWT_SECRET stops the application at startup instead of producing weak tokens.
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(properties.secret()));
        this.parser = Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(properties.issuer())
                .clock(() -> Date.from(clock.instant()))
                .build();
    }

    public String generateToken(UserPrincipal principal) {
        Instant now = clock.instant();
        return Jwts.builder()
                // The subject is the immutable user id, not the email (which a user may change later).
                .subject(String.valueOf(principal.getId()))
                // Informational claims for the frontend (show name/role without another call).
                // The backend never trusts them: JwtFilter reloads the user from the database.
                .claim(CLAIM_EMAIL, principal.getEmail())
                .claim(CLAIM_ROLE, principal.getRole().name())
                .issuer(properties.issuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(properties.expirationMs())))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Verifies signature, issuer and expiry, and returns the user id.
     *
     * @throws JwtException if the token is malformed, tampered with, expired or signed with another key
     */
    public Long extractUserId(String token) {
        Claims claims = parser.parseSignedClaims(token).getPayload();
        try {
            return Long.valueOf(claims.getSubject());
        } catch (NumberFormatException e) {
            throw new JwtException("Token subject is not a user id");
        }
    }

    public long getExpirationSeconds() {
        return properties.expirationMs() / 1000;
    }
}

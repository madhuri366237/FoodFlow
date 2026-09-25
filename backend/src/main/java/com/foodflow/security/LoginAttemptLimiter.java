package com.foodflow.security;

import com.foodflow.exception.TooManyRequestsException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Brute-force protection for POST /api/auth/login: at most {@value #MAX_FAILURES} failed
 * attempts per (email + client IP) within {@link #WINDOW}; further attempts get 429.
 *
 * <p>Why the pair, not email or IP alone?
 * <ul>
 *   <li>Email alone: an attacker could deliberately lock a real user out by failing logins on
 *       their account ("lockout DoS").</li>
 *   <li>IP alone: one IP could try a few passwords against thousands of accounts.</li>
 * </ul>
 * BCrypt already makes each guess slow; this caps how many guesses are possible at all.
 *
 * <p>Limitation: counts live in this JVM's memory. With several backend instances behind a load
 * balancer, each has its own counts. A shared store (Redis) would be the next step.
 */
@Component
public class LoginAttemptLimiter {

    static final int MAX_FAILURES = 5;
    static final Duration WINDOW = Duration.ofMinutes(15);
    private static final int CLEANUP_THRESHOLD = 10_000;

    private final Clock clock;
    // key -> timestamps of recent failures (oldest first)
    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();

    public LoginAttemptLimiter(Clock clock) {
        this.clock = clock;
    }

    /** Throws 429 if this email+IP pair has used up its failed attempts. */
    public void checkAllowed(String email, String clientIp) {
        Deque<Instant> recent = failures.get(key(email, clientIp));
        if (recent == null) {
            return;
        }
        synchronized (recent) {
            dropExpired(recent);
            if (recent.size() >= MAX_FAILURES) {
                long secondsLeft = Duration.between(clock.instant(), recent.peekFirst().plus(WINDOW)).toSeconds();
                long minutes = Math.max(1, (secondsLeft + 59) / 60); // round UP: 14m01s -> "15 minutes"
                throw new TooManyRequestsException(
                        "Too many failed login attempts. Try again in " + minutes + " minute" + (minutes == 1 ? "" : "s") + ".");
            }
        }
    }

    public void recordFailure(String email, String clientIp) {
        Deque<Instant> recent = failures.computeIfAbsent(key(email, clientIp), k -> new ArrayDeque<>());
        synchronized (recent) {
            dropExpired(recent);
            recent.addLast(clock.instant());
        }
        if (failures.size() > CLEANUP_THRESHOLD) {
            purgeExpired(); // keep memory bounded when many different keys fail
        }
    }

    /** A successful login clears the counter for that pair. */
    public void recordSuccess(String email, String clientIp) {
        failures.remove(key(email, clientIp));
    }

    private void dropExpired(Deque<Instant> recent) {
        Instant cutoff = clock.instant().minus(WINDOW);
        while (!recent.isEmpty() && recent.peekFirst().isBefore(cutoff)) {
            recent.pollFirst();
        }
    }

    private void purgeExpired() {
        failures.entrySet().removeIf(entry -> {
            synchronized (entry.getValue()) {
                dropExpired(entry.getValue());
                return entry.getValue().isEmpty();
            }
        });
    }

    private static String key(String email, String clientIp) {
        return (email == null ? "" : email.trim().toLowerCase()) + "|" + clientIp;
    }
}

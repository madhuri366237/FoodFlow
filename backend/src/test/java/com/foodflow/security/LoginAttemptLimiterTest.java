package com.foodflow.security;

import com.foodflow.exception.TooManyRequestsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginAttemptLimiterTest {

    /** A clock the test can move forward, to check the 15-minute window without waiting. */
    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T10:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private MutableClock clock;
    private LoginAttemptLimiter limiter;

    @BeforeEach
    void setUp() {
        clock = new MutableClock();
        limiter = new LoginAttemptLimiter(clock);
    }

    private void fail(String email, String ip, int times) {
        for (int i = 0; i < times; i++) {
            limiter.recordFailure(email, ip);
        }
    }

    @Test
    void fifthFailureLocksThePair() {
        fail("asha@example.com", "1.1.1.1", 4);
        assertThatCode(() -> limiter.checkAllowed("asha@example.com", "1.1.1.1")).doesNotThrowAnyException();

        fail("asha@example.com", "1.1.1.1", 1);
        assertThatThrownBy(() -> limiter.checkAllowed("ASHA@example.com", "1.1.1.1"))
                .isInstanceOf(TooManyRequestsException.class)
                .hasMessageContaining("Try again in 15 minutes");
    }

    @Test
    void attackerCannotLockTheRealUserOutFromAnotherIp() {
        fail("asha@example.com", "6.6.6.6", 10);

        assertThatCode(() -> limiter.checkAllowed("asha@example.com", "1.1.1.1")).doesNotThrowAnyException();
    }

    @Test
    void lockExpiresAfterTheWindow() {
        fail("asha@example.com", "1.1.1.1", 5);

        clock.advance(LoginAttemptLimiter.WINDOW.plusSeconds(1));

        assertThatCode(() -> limiter.checkAllowed("asha@example.com", "1.1.1.1")).doesNotThrowAnyException();
    }

    @Test
    void successResetsTheCounter() {
        fail("asha@example.com", "1.1.1.1", 4);
        limiter.recordSuccess("asha@example.com", "1.1.1.1");
        fail("asha@example.com", "1.1.1.1", 4);

        assertThatCode(() -> limiter.checkAllowed("asha@example.com", "1.1.1.1")).doesNotThrowAnyException();
    }
}

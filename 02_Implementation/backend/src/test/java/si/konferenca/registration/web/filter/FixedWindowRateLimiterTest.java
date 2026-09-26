package si.konferenca.registration.web.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class FixedWindowRateLimiterTest {

  private final AtomicLong millis = new AtomicLong(1_000_000);

  private final Clock clock =
      new Clock() {
        @Override
        public ZoneOffset getZone() {
          return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
          return this;
        }

        @Override
        public Instant instant() {
          return Instant.ofEpochMilli(millis.get());
        }
      };

  @Test
  void allowsUpToLimitThenRejectsWithRetryAfter() {
    FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(3, Duration.ofMinutes(1), clock);
    assertThat(limiter.tryAcquire("a")).isZero();
    assertThat(limiter.tryAcquire("a")).isZero();
    assertThat(limiter.tryAcquire("a")).isZero();
    assertThat(limiter.tryAcquire("a")).isEqualTo(60);
    millis.addAndGet(30_000);
    assertThat(limiter.tryAcquire("a")).isEqualTo(30);
  }

  @Test
  void limitsEachClientSeparately() {
    FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(1, Duration.ofMinutes(1), clock);
    assertThat(limiter.tryAcquire("a")).isZero();
    assertThat(limiter.tryAcquire("b")).isZero();
    assertThat(limiter.tryAcquire("a")).isPositive();
  }

  @Test
  void resetsAfterWindow() {
    FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(1, Duration.ofMinutes(1), clock);
    assertThat(limiter.tryAcquire("a")).isZero();
    assertThat(limiter.tryAcquire("a")).isPositive();
    millis.addAndGet(60_000);
    assertThat(limiter.tryAcquire("a")).isZero();
  }
}

package lab.conference.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class TokenBucketLimiterTest {

  /** Clock that tests can advance. */
  static final class MutableClock extends Clock {
    Instant now = Instant.parse("2026-01-01T00:00:00Z");

    @Override
    public java.time.ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(java.time.ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }

  @Test
  void allowsBurstThenLimitsAndRefills() {
    MutableClock clock = new MutableClock();
    TokenBucketLimiter limiter = new TokenBucketLimiter(3, clock);
    assertThat(limiter.tryAcquire("a")).isZero();
    assertThat(limiter.tryAcquire("a")).isZero();
    assertThat(limiter.tryAcquire("a")).isZero();
    assertThat(limiter.tryAcquire("a")).isEqualTo(20);
    assertThat(limiter.tryAcquire("b")).as("separate key").isZero();
    clock.now = clock.now.plus(Duration.ofSeconds(19));
    assertThat(limiter.tryAcquire("a")).isEqualTo(1);
    clock.now = clock.now.plus(Duration.ofSeconds(1));
    assertThat(limiter.tryAcquire("a")).isZero();
    assertThat(limiter.tryAcquire("a")).isPositive();
  }

  @Test
  void refillIsCappedAtCapacity() {
    MutableClock clock = new MutableClock();
    TokenBucketLimiter limiter = new TokenBucketLimiter(2, clock);
    limiter.tryAcquire("a");
    clock.now = clock.now.plus(Duration.ofHours(1));
    assertThat(limiter.tryAcquire("a")).isZero();
    assertThat(limiter.tryAcquire("a")).isZero();
    assertThat(limiter.tryAcquire("a")).isPositive();
  }

  @Test
  void backwardsClockDoesNotAddTokens() {
    MutableClock clock = new MutableClock();
    TokenBucketLimiter limiter = new TokenBucketLimiter(1, clock);
    limiter.tryAcquire("a");
    clock.now = clock.now.minus(Duration.ofMinutes(5));
    assertThat(limiter.tryAcquire("a")).isPositive();
  }

  @Test
  void prunesFullBucketsWhenManyKeysExist() {
    MutableClock clock = new MutableClock();
    TokenBucketLimiter limiter = new TokenBucketLimiter(1, clock);
    for (int i = 0; i < 10_050; i++) {
      limiter.tryAcquire("k" + i);
    }
    clock.now = clock.now.plus(Duration.ofMinutes(2));
    assertThat(limiter.tryAcquire("k0")).isZero();
    assertThat(limiter.tryAcquire("k0")).isPositive();
  }

  @Test
  void rejectsNonPositiveLimit() {
    assertThatThrownBy(() -> new TokenBucketLimiter(0, Clock.systemUTC()))
        .isInstanceOf(IllegalArgumentException.class);
  }
}

package org.conference.registration.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

class RateLimiterTest {

  private final MutableClock clock = new MutableClock(Instant.parse("2026-05-01T10:00:00Z"));
  private final RateLimiter limiter =
      new RateLimiter(TestProperties.create("x", TestProperties.SECRET, 3, 3), clock);

  @Test
  void allowsUpToLimitThenRejectsWithRetryAfter() {
    for (int i = 0; i < 3; i++) {
      assertThat(limiter.tryAcquire(RateLimiter.Bucket.REGISTRATION, "1.1.1.1").allowed()).isTrue();
    }
    RateLimiter.Decision denied = limiter.tryAcquire(RateLimiter.Bucket.REGISTRATION, "1.1.1.1");
    assertThat(denied.allowed()).isFalse();
    assertThat(denied.retryAfterSeconds()).isEqualTo(600);
  }

  @Test
  void clientsAndBucketsAreIndependent() {
    for (int i = 0; i < 3; i++) {
      limiter.tryAcquire(RateLimiter.Bucket.REGISTRATION, "1.1.1.1");
    }
    assertThat(limiter.tryAcquire(RateLimiter.Bucket.REGISTRATION, "2.2.2.2").allowed()).isTrue();
    assertThat(limiter.tryAcquire(RateLimiter.Bucket.FORM_TOKEN, "1.1.1.1").allowed()).isTrue();
  }

  @Test
  void windowResetsAfterExpiry() {
    for (int i = 0; i < 4; i++) {
      limiter.tryAcquire(RateLimiter.Bucket.REGISTRATION, "1.1.1.1");
    }
    clock.advance(Duration.ofSeconds(600));
    assertThat(limiter.tryAcquire(RateLimiter.Bucket.REGISTRATION, "1.1.1.1").allowed()).isTrue();
  }

  @Test
  void concurrentRequestsNeverExceedLimit() throws Exception {
    int threads = 64;
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    CountDownLatch start = new CountDownLatch(1);
    List<Future<Boolean>> results = new ArrayList<>();
    for (int i = 0; i < threads; i++) {
      results.add(
          pool.submit(
              () -> {
                start.await();
                return limiter.tryAcquire(RateLimiter.Bucket.REGISTRATION, "9.9.9.9").allowed();
              }));
    }
    start.countDown();
    int allowed = 0;
    for (Future<Boolean> result : results) {
      if (result.get()) {
        allowed++;
      }
    }
    pool.shutdown();
    assertThat(allowed).isEqualTo(3);
  }
}

package org.conference.registration.service;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.conference.registration.config.AppProperties;
import org.springframework.stereotype.Service;

/**
 * Per-client fixed-window rate limiter (AC-PC-05). The counter update is a single atomic {@link
 * ConcurrentHashMap#compute} call, so concurrent requests cannot both pass a check-then-increment
 * race.
 */
@Service
public class RateLimiter {

  private static final int CLEANUP_THRESHOLD = 50_000;

  /** Independently limited request classes. */
  public enum Bucket {
    REGISTRATION,
    FORM_TOKEN,
    ADMIN
  }

  private final Clock clock;
  private final long windowMillis;
  private final Map<Bucket, Integer> limits;
  private final Map<String, Window> windows = new ConcurrentHashMap<>();

  public RateLimiter(AppProperties properties, Clock clock) {
    AppProperties.RateLimit config = properties.rateLimit();
    this.clock = clock;
    this.windowMillis = config.windowSeconds() * 1000;
    this.limits =
        Map.of(
            Bucket.REGISTRATION, config.registrations(),
            Bucket.FORM_TOKEN, config.formTokens(),
            Bucket.ADMIN, config.admin());
  }

  /** Counts one request for {@code clientKey} and decides whether it may proceed. */
  public Decision tryAcquire(Bucket bucket, String clientKey) {
    long now = clock.millis();
    Window window =
        windows.compute(
            bucket.name() + '|' + clientKey,
            (key, current) ->
                current == null || now - current.start() >= windowMillis
                    ? new Window(now, 1)
                    : new Window(current.start(), current.count() + 1));
    if (windows.size() > CLEANUP_THRESHOLD) {
      windows.values().removeIf(w -> now - w.start() >= windowMillis);
    }
    if (window.count() <= limits.get(bucket)) {
      return new Decision(true, 0);
    }
    long retryAfterMillis = window.start() + windowMillis - now;
    return new Decision(false, Math.max(1, (retryAfterMillis + 999) / 1000));
  }

  private record Window(long start, int count) {}

  /** Outcome of a rate-limit check; {@code retryAfterSeconds} is 0 when allowed. */
  public record Decision(boolean allowed, long retryAfterSeconds) {}
}

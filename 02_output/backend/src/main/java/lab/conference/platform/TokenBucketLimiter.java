package lab.conference.platform;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * In-memory token bucket per key (client address): {@code perMinute} requests, refilled evenly over
 * one minute (SB-06, SR-03). Idle buckets are pruned when the map grows large.
 */
public final class TokenBucketLimiter {

  private static final long MINUTE_NANOS = 60_000_000_000L;
  private static final int PRUNE_THRESHOLD = 10_000;
  private static final double ROUNDING_TOLERANCE_SECONDS = 1e-6;
  private final int perMinute;
  private final Clock clock;
  private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

  public TokenBucketLimiter(int perMinute, Clock clock) {
    if (perMinute < 1) {
      throw new IllegalArgumentException("rate limit must be at least 1 per minute");
    }
    this.perMinute = perMinute;
    this.clock = clock;
  }

  /** Returns 0 if the request may proceed, else the seconds until a token is available. */
  public long tryAcquire(String key) {
    long now = nanos();
    if (buckets.size() > PRUNE_THRESHOLD) {
      buckets.values().removeIf(b -> b.isFull(now));
    }
    return buckets.computeIfAbsent(key, k -> new Bucket(now)).take(now);
  }

  private long nanos() {
    java.time.Instant now = clock.instant();
    return now.getEpochSecond() * 1_000_000_000L + now.getNano();
  }

  private final class Bucket {
    private final ReentrantLock lock = new ReentrantLock();
    private double tokens;
    private long last;

    Bucket(long now) {
      this.tokens = perMinute;
      this.last = now;
    }

    long take(long now) {
      lock.lock();
      try {
        refill(now);
        if (tokens >= 1) {
          tokens -= 1;
          return 0;
        }
        double nanosPerToken = (double) MINUTE_NANOS / perMinute;
        double seconds = (1 - tokens) * nanosPerToken / 1_000_000_000d;
        return Math.max(1, (long) Math.ceil(seconds - ROUNDING_TOLERANCE_SECONDS));
      } finally {
        lock.unlock();
      }
    }

    boolean isFull(long now) {
      lock.lock();
      try {
        refill(now);
        return tokens >= perMinute;
      } finally {
        lock.unlock();
      }
    }

    private void refill(long now) {
      long elapsed = Math.max(0, now - last);
      tokens = Math.min(perMinute, tokens + elapsed * (double) perMinute / MINUTE_NANOS);
      last = now;
    }
  }
}

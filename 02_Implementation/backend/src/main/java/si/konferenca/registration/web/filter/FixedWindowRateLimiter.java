package si.konferenca.registration.web.filter;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory fixed-window rate limiter keyed by client address. */
public class FixedWindowRateLimiter {

  static final int MAX_TRACKED_CLIENTS = 10_000;

  private final int maxRequests;
  private final long windowMillis;
  private final Clock clock;
  private final Map<String, Window> windows = new ConcurrentHashMap<>();

  public FixedWindowRateLimiter(int maxRequests, Duration window, Clock clock) {
    this.maxRequests = maxRequests;
    this.windowMillis = window.toMillis();
    this.clock = clock;
  }

  /**
   * Records a request for {@code key}.
   *
   * @return 0 if the request is allowed, otherwise the number of seconds until the window resets
   */
  public long tryAcquire(String key) {
    long now = clock.millis();
    if (windows.size() >= MAX_TRACKED_CLIENTS) {
      windows.values().removeIf(w -> w.isExpired(now, windowMillis));
    }
    if (windows.size() >= MAX_TRACKED_CLIENTS && !windows.containsKey(key)) {
      return Math.max(1, windowMillis / 1000);
    }
    Window window =
        windows.compute(
            key,
            (k, existing) ->
                existing == null || existing.isExpired(now, windowMillis)
                    ? new Window(now, 1)
                    : existing.increment());
    if (window.count() <= maxRequests) {
      return 0;
    }
    long remainingMillis = window.start() + windowMillis - now;
    return Math.max(1, (remainingMillis + 999) / 1000);
  }

  private record Window(long start, int count) {
    Window increment() {
      return new Window(start, count + 1);
    }

    boolean isExpired(long now, long windowMillis) {
      return now - start >= windowMillis;
    }
  }
}

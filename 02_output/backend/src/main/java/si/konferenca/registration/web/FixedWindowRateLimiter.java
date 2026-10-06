package si.konferenca.registration.web;

import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** In-memory requests-per-minute limit per key (SR-03, SB-06); one backend instance. */
final class FixedWindowRateLimiter {

  private static final long WINDOW_MS = 60_000;
  private static final int CLEANUP_THRESHOLD = 10_000;

  private record Window(long start, int count) {}

  private final ConcurrentMap<String, Window> windows = new ConcurrentHashMap<>();
  private final Clock clock;

  FixedWindowRateLimiter(Clock clock) {
    this.clock = clock;
  }

  /** Returns 0 when the request is allowed, otherwise the seconds until the window ends. */
  long acquire(String key, int limit) {
    long now = clock.millis();
    long windowStart = now - now % WINDOW_MS;
    if (windows.size() > CLEANUP_THRESHOLD) {
      windows.values().removeIf(w -> w.start() < windowStart);
    }
    Window window =
        windows.compute(
            key,
            (k, current) ->
                current == null || current.start() != windowStart
                    ? new Window(windowStart, 1)
                    : new Window(windowStart, current.count() + 1));
    if (window.count() <= limit) {
      return 0;
    }
    return Math.max(1, (windowStart + WINDOW_MS - now + 999) / 1000);
  }
}

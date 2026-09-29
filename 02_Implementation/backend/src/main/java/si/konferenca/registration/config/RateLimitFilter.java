package si.konferenca.registration.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Per-client-IP fixed-window rate limiting (specification §8.3): registration submissions and all
 * organizer endpoints have separate budgets.
 */
public class RateLimitFilter extends OncePerRequestFilter {

  static final long WINDOW_MILLIS = 60_000;
  static final int MAX_TRACKED_KEYS = 10_000;

  private final int registrationLimit;
  private final int organizerLimit;
  private final Clock clock;
  private final Map<String, Window> windows = new ConcurrentHashMap<>();

  public RateLimitFilter(int registrationLimit, int organizerLimit, Clock clock) {
    this.registrationLimit = registrationLimit;
    this.organizerLimit = organizerLimit;
    this.clock = clock;
  }

  private record Window(long start, int count) {}

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String path = request.getRequestURI().substring(request.getContextPath().length());
    String bucket;
    int limit;
    if ("POST".equals(request.getMethod()) && "/api/registrations".equals(path)) {
      bucket = "registration";
      limit = registrationLimit;
    } else if (path.startsWith("/api/organizer/")) {
      bucket = "organizer";
      limit = organizerLimit;
    } else {
      chain.doFilter(request, response);
      return;
    }
    long retryAfterSeconds = consume(bucket + "|" + request.getRemoteAddr(), limit);
    if (retryAfterSeconds > 0) {
      response.setHeader("Retry-After", Long.toString(retryAfterSeconds));
      ProblemResponses.write(
          response,
          429,
          "Too many requests",
          "RATE_LIMITED",
          "Too many attempts, please wait and try again.");
      return;
    }
    chain.doFilter(request, response);
  }

  /** Counts one request; returns 0 if allowed, otherwise the seconds until the window resets. */
  long consume(String key, int limit) {
    long now = clock.millis();
    if (windows.size() > MAX_TRACKED_KEYS) {
      windows.values().removeIf(w -> now - w.start() >= WINDOW_MILLIS);
      if (windows.size() > MAX_TRACKED_KEYS) {
        windows.clear();
      }
    }
    Window window =
        windows.compute(
            key,
            (k, w) ->
                w == null || now - w.start() >= WINDOW_MILLIS
                    ? new Window(now, 1)
                    : new Window(w.start(), w.count() + 1));
    if (window.count() <= limit) {
      return 0;
    }
    long remaining = WINDOW_MILLIS - (now - window.start());
    return Math.max(1, (remaining + 999) / 1000);
  }
}

package si.konferenca.registration.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.web.filter.OncePerRequestFilter;
import si.konferenca.registration.settings.AppProperties;

/**
 * Per-client, per-bucket request limits in one-minute windows (SR-03, SB-06; 02_specification.md
 * 5.2). The client is the remote address after the trusted-proxy valve. Export requests are counted
 * before authentication, so failed logins count too.
 */
public class RateLimitFilter extends OncePerRequestFilter {

  static final long WINDOW_MILLIS = 60_000;
  static final int MAX_TRACKED_CLIENTS = 100_000;

  private final AppProperties.RateLimit limits;
  private final Clock clock;
  private final Map<String, Window> windows = new ConcurrentHashMap<>();

  public RateLimitFilter(AppProperties.RateLimit limits, Clock clock) {
    this.limits = limits;
    this.clock = clock;
  }

  /** Requests counted in the window that started at {@code start}. */
  private record Window(long start, int count) {}

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String bucket = bucket(request);
    if (bucket == null) {
      chain.doFilter(request, response);
      return;
    }
    long retryAfterMillis = acquire(bucket + "|" + request.getRemoteAddr(), limitOf(bucket));
    if (retryAfterMillis > 0) {
      response.setHeader("Retry-After", String.valueOf(Math.max(1, retryAfterMillis / 1000)));
      ErrorResponses.write(
          response, ErrorBody.of(429, "rate_limited", "Too many requests. Please wait a minute."));
      return;
    }
    chain.doFilter(request, response);
  }

  static String bucket(HttpServletRequest request) {
    String path = request.getRequestURI();
    if (path.startsWith("/api/organizer/")) {
      return "export";
    }
    if ("/api/registrations".equals(path)) {
      return "registration";
    }
    if ("/api/options".equals(path) || "/api/config".equals(path)) {
      return "read";
    }
    return null;
  }

  private int limitOf(String bucket) {
    return switch (bucket) {
      case "export" -> limits.exportPerMinute();
      case "registration" -> limits.registrationPerMinute();
      default -> limits.readPerMinute();
    };
  }

  /** Counts the request; returns 0 when allowed, else the milliseconds until the window ends. */
  long acquire(String key, int limit) {
    long now = clock.millis();
    if (windows.size() > MAX_TRACKED_CLIENTS) {
      windows.values().removeIf(w -> now - w.start() >= WINDOW_MILLIS);
    }
    Window w =
        windows.compute(
            key,
            (k, old) ->
                old == null || now - old.start() >= WINDOW_MILLIS
                    ? new Window(now, 1)
                    : new Window(old.start(), old.count() + 1));
    return w.count() > limit ? w.start() + WINDOW_MILLIS - now : 0;
  }
}

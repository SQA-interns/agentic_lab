package si.konferenca.registration.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import si.konferenca.registration.config.AppProperties;
import si.konferenca.registration.config.SecurityConfig;

/**
 * Fixed one-minute window per client address for registration and export requests (SR-03, SB-06).
 * Failed organizer logins count too, because the filter runs before authentication.
 */
@Component
@Order(-130)
public class RateLimitFilter extends OncePerRequestFilter {

  private static final long WINDOW_MILLIS = 60_000;
  private static final int MAX_TRACKED = 10_000;

  private final int registrationsPerMinute;
  private final int exportsPerMinute;
  private final Clock clock;
  private final Map<String, Window> windows = new ConcurrentHashMap<>();

  @Autowired
  public RateLimitFilter(AppProperties properties) {
    this(properties, Clock.systemUTC());
  }

  RateLimitFilter(AppProperties properties, Clock clock) {
    this.registrationsPerMinute = properties.rateLimit().registrationsPerMinute();
    this.exportsPerMinute = properties.rateLimit().exportsPerMinute();
    this.clock = clock;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return limitFor(request) == 0;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    long now = clock.millis();
    long windowStart = now - now % WINDOW_MILLIS;
    String key = request.getRequestURI() + "|" + request.getRemoteAddr();
    if (windows.size() > MAX_TRACKED) {
      windows.values().removeIf(w -> w.start() < windowStart);
    }
    Window window =
        windows.compute(
            key,
            (k, w) ->
                w == null || w.start() != windowStart
                    ? new Window(windowStart, 1)
                    : new Window(windowStart, w.count() + 1));
    if (window.count() > limitFor(request)) {
      long retryAfter = Math.max(1, (windowStart + WINDOW_MILLIS - now + 999) / 1000);
      response.setHeader("Retry-After", String.valueOf(retryAfter));
      SecurityConfig.writeProblem(
          response, 429, "Too many attempts, please try again in a minute.");
      return;
    }
    chain.doFilter(request, response);
  }

  private int limitFor(HttpServletRequest request) {
    String uri = request.getRequestURI();
    if ("POST".equals(request.getMethod()) && "/api/registrations".equals(uri)) {
      return registrationsPerMinute;
    }
    if ("/api/export".equals(uri)) {
      return exportsPerMinute;
    }
    return 0;
  }

  private record Window(long start, int count) {}
}

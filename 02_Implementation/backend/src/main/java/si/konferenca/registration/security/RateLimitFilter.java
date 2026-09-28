package si.konferenca.registration.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import si.konferenca.registration.config.AppProperties;

/** In-memory fixed-window rate limiting per client IP for registration and organizer requests. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class RateLimitFilter extends OncePerRequestFilter {

  private static final int CLEANUP_THRESHOLD = 10_000;

  private final AppProperties.Limit registrationLimit;
  private final AppProperties.Limit organizerLimit;
  private final Clock clock;
  private final Map<String, Window> windows = new ConcurrentHashMap<>();

  public RateLimitFilter(AppProperties properties, Clock clock) {
    this.registrationLimit = properties.rateLimit().registration();
    this.organizerLimit = properties.rateLimit().organizer();
    this.clock = clock;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String path = request.getRequestURI();
    String scope;
    AppProperties.Limit limit;
    if (HttpMethod.POST.matches(request.getMethod()) && path.startsWith("/api/registrations")) {
      scope = "registration";
      limit = registrationLimit;
    } else if (path.startsWith("/api/organizer")) {
      scope = "organizer";
      limit = organizerLimit;
    } else {
      chain.doFilter(request, response);
      return;
    }
    long retryAfter = acquire(scope + "|" + request.getRemoteAddr(), limit);
    if (retryAfter > 0) {
      response.setHeader("Retry-After", Long.toString(retryAfter));
      JsonErrorWriter.write(
          response, 429, "RATE_LIMITED", "Too many requests. Please try again later.");
      return;
    }
    chain.doFilter(request, response);
  }

  /** Returns 0 if the request is allowed, otherwise the seconds until the window resets. */
  private long acquire(String key, AppProperties.Limit limit) {
    long now = clock.millis();
    long windowMillis = limit.windowSeconds() * 1000;
    if (windows.size() > CLEANUP_THRESHOLD) {
      windows.values().removeIf(w -> now - w.start >= windowMillis);
    }
    Window window =
        windows.compute(
            key,
            (k, existing) -> {
              if (existing == null || now - existing.start >= windowMillis) {
                return new Window(now, 1);
              }
              return new Window(existing.start, existing.count + 1);
            });
    if (window.count <= limit.requests()) {
      return 0;
    }
    return Math.max(1, (window.start + windowMillis - now + 999) / 1000);
  }

  private record Window(long start, int count) {}
}

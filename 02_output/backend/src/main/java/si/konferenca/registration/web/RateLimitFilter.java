package si.konferenca.registration.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Fixed one-minute window per client address and endpoint group (SR-03, SB-06). Limits are generous
 * enough for many participants behind one shared address.
 */
public class RateLimitFilter extends OncePerRequestFilter {

  private static final long WINDOW_MILLIS = 60_000;
  private static final int MAX_TRACKED = 50_000;

  private final int registrationLimit;
  private final int exportLimit;
  private final int optionsLimit;
  private final Clock clock;
  private final Map<String, long[]> windows = new ConcurrentHashMap<>();

  public RateLimitFilter(int registrationLimit, int exportLimit, int optionsLimit, Clock clock) {
    this.registrationLimit = registrationLimit;
    this.exportLimit = exportLimit;
    this.optionsLimit = optionsLimit;
    this.clock = clock;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String path = request.getRequestURI();
    String group;
    int limit;
    if (path.startsWith("/api/admin/")) {
      group = "export";
      limit = exportLimit;
    } else if (path.startsWith("/api/registrations")) {
      group = "registration";
      limit = registrationLimit;
    } else if (path.startsWith("/api/options")) {
      group = "options";
      limit = optionsLimit;
    } else {
      chain.doFilter(request, response);
      return;
    }
    long now = clock.millis();
    long retryAfter = acquire(group + "|" + request.getRemoteAddr(), limit, now);
    if (retryAfter > 0) {
      response.setHeader("Retry-After", String.valueOf(retryAfter));
      Problems.write(response, HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "Too many requests");
      return;
    }
    chain.doFilter(request, response);
  }

  /** Counts the request; returns 0 if allowed, else seconds until the window resets. */
  long acquire(String key, int limit, long now) {
    if (windows.size() > MAX_TRACKED) {
      windows.entrySet().removeIf(e -> now - e.getValue()[0] >= WINDOW_MILLIS);
    }
    long[] w =
        windows.compute(
            key,
            (k, old) -> {
              if (old == null || now - old[0] >= WINDOW_MILLIS) {
                return new long[] {now, 1};
              }
              old[1]++;
              return old;
            });
    if (w[1] <= limit) {
      return 0;
    }
    return Math.max(1, (w[0] + WINDOW_MILLIS - now + 999) / 1000);
  }
}

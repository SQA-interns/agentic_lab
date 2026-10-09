package si.konferenca.registration.api;

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
 * Fixed one-minute windows per client address and endpoint group (SR-03, SB-06). The client address
 * is the one Tomcat derives from trusted proxy headers.
 */
public class RateLimitFilter extends OncePerRequestFilter {

  /** Rate-limited endpoint groups. */
  public enum Group {
    REGISTRATIONS("POST", "/api/registrations"),
    EXPORTS("GET", "/api/export"),
    FORM("GET", "/api/registration-form");

    private final String method;
    private final String path;

    Group(String method, String path) {
      this.method = method;
      this.path = path;
    }

    static Group of(HttpServletRequest request) {
      for (Group group : values()) {
        if (group.method.equals(request.getMethod())
            && group.path.equals(request.getRequestURI())) {
          return group;
        }
      }
      return null;
    }
  }

  private static final long MINUTE_MILLIS = 60_000;
  private static final int MAX_TRACKED_CLIENTS = 10_000;

  private record Window(long minute, int count) {}

  private final Map<Group, Integer> limits;
  private final Clock clock;
  private final Map<String, Window> windows = new ConcurrentHashMap<>();

  public RateLimitFilter(Map<Group, Integer> limits, Clock clock) {
    this.limits = Map.copyOf(limits);
    this.clock = clock;
  }

  /** Number of client windows held in memory (bounded by the cleanup below). */
  int trackedWindows() {
    return windows.size();
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    Group group = Group.of(request);
    if (group == null) {
      chain.doFilter(request, response);
      return;
    }
    long now = clock.millis();
    long minute = now / MINUTE_MILLIS;
    if (windows.size() > MAX_TRACKED_CLIENTS) {
      windows.values().removeIf(window -> window.minute() < minute);
    }
    Window window =
        windows.compute(
            group + "|" + request.getRemoteAddr(),
            (key, old) ->
                old == null || old.minute() != minute
                    ? new Window(minute, 1)
                    : new Window(minute, old.count() + 1));
    if (window.count() > limits.getOrDefault(group, Integer.MAX_VALUE)) {
      long retryAfterSeconds = Math.max(1, ((minute + 1) * MINUTE_MILLIS - now + 999) / 1000);
      response.setHeader("Retry-After", Long.toString(retryAfterSeconds));
      ErrorResponseWriter.write(
          response,
          429,
          ErrorResponse.of(
              "rate_limited", "Too many requests. Please wait a minute and try again."));
      return;
    }
    chain.doFilter(request, response);
  }
}

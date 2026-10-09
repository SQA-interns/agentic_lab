package si.konferenca.registration.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Fixed one-minute window per client address and endpoint group (SB-06, SR-03). The client address
 * is the one Tomcat resolved from trusted proxy headers.
 */
public class RateLimitFilter extends OncePerRequestFilter {

  private static final long WINDOW_MILLIS = 60_000;
  private static final int PRUNE_THRESHOLD = 10_000;

  private record Window(long start, int count) {}

  private final int registrations;
  private final int exports;
  private final int forms;
  private final LongSupplier clock;
  private final Map<String, Window> windows = new ConcurrentHashMap<>();

  public RateLimitFilter(int registrations, int exports, int forms, LongSupplier clock) {
    this.registrations = registrations;
    this.exports = exports;
    this.forms = forms;
    this.clock = clock;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String group = group(request);
    if (group == null) {
      chain.doFilter(request, response);
      return;
    }
    int limit = limit(group);
    long now = clock.getAsLong();
    String key = group + "|" + request.getRemoteAddr();
    Window window =
        windows.compute(
            key,
            (k, w) ->
                w == null || now - w.start() >= WINDOW_MILLIS
                    ? new Window(now, 1)
                    : new Window(w.start(), w.count() + 1));
    prune(now);
    if (window.count() > limit) {
      long retry = Math.max(1, (window.start() + WINDOW_MILLIS - now + 999) / 1000);
      response.setHeader("Retry-After", String.valueOf(retry));
      Problems.write(response, HttpStatus.TOO_MANY_REQUESTS);
      return;
    }
    chain.doFilter(request, response);
  }

  private static String group(HttpServletRequest request) {
    String path = request.getRequestURI();
    if ("POST".equals(request.getMethod()) && "/api/registrations".equals(path)) {
      return "registrations";
    }
    if ("/api/registrations/export".equals(path)) {
      return "exports";
    }
    if (path.startsWith("/api/registration-form/")) {
      return "forms";
    }
    return null;
  }

  private int limit(String group) {
    return switch (group) {
      case "registrations" -> registrations;
      case "exports" -> exports;
      default -> forms;
    };
  }

  private void prune(long now) {
    if (windows.size() > PRUNE_THRESHOLD) {
      windows.values().removeIf(w -> now - w.start() >= WINDOW_MILLIS);
    }
  }
}

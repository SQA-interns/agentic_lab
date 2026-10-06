package si.konferenca.registration.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Fixed one-minute window per client address and endpoint group (SR-03, SB-06,
 * docs/02_specification.md §8). Requests over the limit get 429 with Retry-After.
 */
public class RateLimitFilter extends OncePerRequestFilter {

  private static final long WINDOW_MILLIS = 60_000;
  private static final int CLEANUP_THRESHOLD = 10_000;

  private final int registrationsPerMinute;
  private final int exportsPerMinute;
  private final int formConfigPerMinute;
  private final Clock clock;
  private final Map<String, Window> windows = new ConcurrentHashMap<>();

  public RateLimitFilter(
      int registrationsPerMinute, int exportsPerMinute, int formConfigPerMinute, Clock clock) {
    this.registrationsPerMinute = registrationsPerMinute;
    this.exportsPerMinute = exportsPerMinute;
    this.formConfigPerMinute = formConfigPerMinute;
    this.clock = clock;
  }

  private record Window(long start, int count) {}

  /** The limited group of a request and its limit, or null when the request is not limited. */
  private record Group(String name, int limit) {}

  private Group groupOf(HttpServletRequest request) {
    String path = request.getRequestURI();
    String method = request.getMethod();
    if ("POST".equals(method) && "/api/registrations".equals(path)) {
      return new Group("registrations", registrationsPerMinute);
    }
    if ("/api/registrations/export".equals(path)) {
      return new Group("export", exportsPerMinute);
    }
    if ("/api/form-config".equals(path)) {
      return new Group("form-config", formConfigPerMinute);
    }
    return null;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    Group group = groupOf(request);
    if (group == null) {
      chain.doFilter(request, response);
      return;
    }
    long now = clock.millis();
    Window window =
        windows.compute(
            group.name() + "|" + request.getRemoteAddr(),
            (key, current) ->
                current == null || now - current.start() >= WINDOW_MILLIS
                    ? new Window(now, 1)
                    : new Window(current.start(), current.count() + 1));
    if (windows.size() > CLEANUP_THRESHOLD) {
      windows.values().removeIf(w -> now - w.start() >= WINDOW_MILLIS);
    }
    if (window.count() > group.limit()) {
      long retryAfter = Math.max(1, (window.start() + WINDOW_MILLIS - now + 999) / 1000);
      response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
      response.setHeader("Retry-After", String.valueOf(retryAfter));
      response.setContentType(Problems.PROBLEM_JSON.toString());
      response
          .getOutputStream()
          .write(
              Problems.json(
                      HttpStatus.TOO_MANY_REQUESTS,
                      "Too many requests",
                      "Please wait a moment and try again.")
                  .getBytes(StandardCharsets.UTF_8));
      return;
    }
    chain.doFilter(request, response);
  }
}

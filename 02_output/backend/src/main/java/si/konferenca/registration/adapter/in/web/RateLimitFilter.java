package si.konferenca.registration.adapter.in.web;

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
 * Fixed-window rate limit per client address and group of operations (SR-03, SB-06). The counters
 * live in memory, which is enough for the single backend instance of this system. Every request to
 * the export counts, so failed organizer logins are limited as well.
 */
public class RateLimitFilter extends OncePerRequestFilter {

  private static final long WINDOW_MILLIS = 60_000L;
  private static final int MAX_TRACKED_CLIENTS = 100_000;

  /** Requests per minute and client for each group. */
  public record Limits(int registration, int export, int read) {}

  private record Window(long start, int count) {}

  private final Limits limits;
  private final ClientRequests clientRequests;
  private final Clock clock;
  private final Map<String, Window> windows = new ConcurrentHashMap<>();

  public RateLimitFilter(Limits limits, ClientRequests clientRequests, Clock clock) {
    this.limits = limits;
    this.clientRequests = clientRequests;
    this.clock = clock;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getRequestURI().startsWith("/api/");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String group;
    int limit;
    if (ExportController.PATH.equals(request.getRequestURI())) {
      group = "export";
      limit = limits.export();
    } else if ("POST".equals(request.getMethod())) {
      group = "registration";
      limit = limits.registration();
    } else {
      group = "read";
      limit = limits.read();
    }
    long now = clock.millis();
    long windowStart = now - now % WINDOW_MILLIS;
    if (windows.size() > MAX_TRACKED_CLIENTS) {
      // Bounded memory: windows that are over are dropped before new clients are tracked.
      windows.values().removeIf(window -> window.start() != windowStart);
    }
    Window window =
        windows.merge(
            group + '|' + clientRequests.clientAddress(request),
            new Window(windowStart, 1),
            (current, fresh) ->
                current.start() == windowStart
                    ? new Window(windowStart, current.count() + 1)
                    : fresh);
    if (window.count() > limit) {
      long retryAfterSeconds = Math.max(1, (windowStart + WINDOW_MILLIS - now + 999) / 1000);
      response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
      ProblemWriter.write(response, HttpStatus.TOO_MANY_REQUESTS.value());
      return;
    }
    chain.doFilter(request, response);
  }
}

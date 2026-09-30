package si.konferenca.registration.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.http.HttpMethod;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Fixed-window rate limit per client address (SR-03, SB-06). The client address is the one the
 * servlet container reports after trusted-proxy processing of {@code X-Forwarded-For}.
 */
class RateLimitFilter extends OncePerRequestFilter {

  /** Upper bound of tracked windows; expired ones are purged when it is reached. */
  static final int MAX_TRACKED = 50_000;

  /** One limited endpoint: a null method matches every method; the path is a prefix. */
  record Rule(String name, HttpMethod method, String pathPrefix, int limit, Duration window) {}

  private record Window(long start, int count) {}

  private final List<Rule> rules;
  private final Clock clock;
  private final ConcurrentMap<String, Window> windows = new ConcurrentHashMap<>();

  RateLimitFilter(List<Rule> rules, Clock clock) {
    this.rules = List.copyOf(rules);
    this.clock = clock;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    for (Rule rule : rules) {
      if (matches(rule, request) && !allow(rule, request.getRemoteAddr())) {
        response.setHeader("Retry-After", String.valueOf(rule.window().toSeconds()));
        JsonErrors.write(response, 429, "Too many requests. Please try again later.");
        return;
      }
    }
    chain.doFilter(request, response);
  }

  private static boolean matches(Rule rule, HttpServletRequest request) {
    return (rule.method() == null || rule.method().matches(request.getMethod()))
        && request.getRequestURI().startsWith(rule.pathPrefix());
  }

  boolean allow(Rule rule, String client) {
    long now = clock.millis();
    long length = rule.window().toMillis();
    if (windows.size() >= MAX_TRACKED) {
      windows.values().removeIf(w -> now - w.start() >= length);
    }
    Window w =
        windows.compute(
            rule.name() + '|' + client,
            (key, current) ->
                current == null || now - current.start() >= length
                    ? new Window(now, 1)
                    : new Window(current.start(), current.count() + 1));
    return w.count() <= rule.limit();
  }
}

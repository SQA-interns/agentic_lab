package org.example.conference.shared.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.example.conference.shared.api.ErrorCode;
import org.example.conference.shared.api.Problems;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Fixed-window per-client-IP limit for public POST submissions. The client IP is the address
 * resolved by Tomcat's RemoteIpValve, which only trusts configured internal proxies. In-memory:
 * valid for the single backend instance of this architecture.
 */
public class RateLimitFilter extends OncePerRequestFilter {

  private static final long WINDOW_MILLIS = 60_000;
  private static final int MAX_TRACKED_CLIENTS = 10_000;

  private final int limitPerWindow;
  private final ObjectMapper objectMapper;
  private final Clock clock;
  private final Map<String, Window> windows = new ConcurrentHashMap<>();

  public RateLimitFilter(int limitPerWindow, ObjectMapper objectMapper, Clock clock) {
    super();
    this.limitPerWindow = limitPerWindow;
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !"POST".equals(request.getMethod());
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (!tryAcquire(request.getRemoteAddr())) {
      response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
      response.setHeader("Retry-After", "60");
      response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
      objectMapper.writeValue(
          response.getOutputStream(),
          Problems.of(HttpStatus.TOO_MANY_REQUESTS, ErrorCode.RATE_LIMITED, List.of()));
      return;
    }
    chain.doFilter(request, response);
  }

  boolean tryAcquire(String clientKey) {
    long now = clock.millis();
    if (windows.size() > MAX_TRACKED_CLIENTS) {
      windows.values().removeIf(w -> now - w.start >= WINDOW_MILLIS);
    }
    Window window =
        windows.compute(
            clientKey,
            (key, current) ->
                current == null || now - current.start >= WINDOW_MILLIS
                    ? new Window(now, 1)
                    : new Window(current.start, current.count + 1));
    return window.count <= limitPerWindow;
  }

  private record Window(long start, int count) {}
}

package si.konferenca.registration.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import org.springframework.web.filter.OncePerRequestFilter;
import si.konferenca.registration.config.AppProperties;

/**
 * Rate limits per client address and endpoint group, and the declared body size limit (SR-03,
 * SB-06). Runs before authentication, so failed organizer logins count against the export limit.
 */
final class RequestLimitsFilter extends OncePerRequestFilter {

  private final FixedWindowRateLimiter limiter;
  private final AppProperties.RateLimit limits;
  private final int maxRequestBytes;

  RequestLimitsFilter(AppProperties app, Clock clock) {
    this.limiter = new FixedWindowRateLimiter(clock);
    this.limits = app.rateLimit();
    this.maxRequestBytes = app.maxRequestBytes();
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getRequestURI().startsWith("/api/");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (request.getContentLengthLong() > maxRequestBytes) {
      ErrorResponses.write(response, 413, "PAYLOAD_TOO_LARGE", ErrorResponses.PAYLOAD_TOO_LARGE);
      return;
    }
    String path = request.getRequestURI();
    String group;
    int limit;
    if (path.startsWith("/api/export/")) {
      group = "export";
      limit = limits.exports();
    } else if (path.equals("/api/registrations")) {
      group = "registrations";
      limit = limits.registrations();
    } else {
      group = "config";
      limit = limits.config();
    }
    long retryAfter = limiter.acquire(group + "|" + request.getRemoteAddr(), limit);
    if (retryAfter > 0) {
      response.setHeader("Retry-After", String.valueOf(retryAfter));
      ErrorResponses.write(response, 429, "RATE_LIMITED", ErrorResponses.RATE_LIMITED);
      return;
    }
    chain.doFilter(request, response);
  }
}

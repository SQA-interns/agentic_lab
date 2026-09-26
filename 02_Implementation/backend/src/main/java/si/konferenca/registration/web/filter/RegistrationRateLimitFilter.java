package si.konferenca.registration.web.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;
import si.konferenca.registration.web.dto.ErrorResponse;

/** Limits registration submissions per client IP address. */
public class RegistrationRateLimitFilter extends OncePerRequestFilter {

  private final FixedWindowRateLimiter limiter;
  private final ErrorResponseWriter errorWriter;

  public RegistrationRateLimitFilter(
      FixedWindowRateLimiter limiter, ErrorResponseWriter errorWriter) {
    this.limiter = limiter;
    this.errorWriter = errorWriter;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !("POST".equals(request.getMethod())
        && "/api/registrations".equals(request.getRequestURI()));
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    long retryAfter = limiter.tryAcquire(request.getRemoteAddr());
    if (retryAfter > 0) {
      response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfter));
      errorWriter.write(
          response,
          429,
          ErrorResponse.of(
              "RATE_LIMITED", "Too many registration attempts. Please try again later."));
      return;
    }
    chain.doFilter(request, response);
  }
}

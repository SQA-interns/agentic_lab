package lab.conference.platform;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Runs before Spring Security: per-address rate limits for registration and export (SB-06), the
 * Origin allow-list for registration POSTs, and the request body size limit (SR-03).
 */
public class RequestGuardFilter extends OncePerRequestFilter {

  public static final int MAX_BODY_BYTES = 16_384;
  private static final String REGISTRATIONS = "/api/registrations/";
  private static final String ORGANIZER = "/api/organizer/";
  private final TokenBucketLimiter registrationLimiter;
  private final TokenBucketLimiter exportLimiter;
  private final Set<String> allowedOrigins;

  public RequestGuardFilter(AppProperties props, Clock clock) {
    this.registrationLimiter =
        new TokenBucketLimiter(props.rateLimit().registrationPerMinute(), clock);
    this.exportLimiter = new TokenBucketLimiter(props.rateLimit().exportPerMinute(), clock);
    this.allowedOrigins =
        props.allowedOrigins() == null ? Set.of() : Set.copyOf(props.allowedOrigins());
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String path = request.getRequestURI();
    boolean registration = path.startsWith(REGISTRATIONS) && "POST".equals(request.getMethod());
    TokenBucketLimiter limiter =
        registration ? registrationLimiter : path.startsWith(ORGANIZER) ? exportLimiter : null;
    if (limiter != null) {
      long retryAfter = limiter.tryAcquire(request.getRemoteAddr());
      if (retryAfter > 0) {
        response.setHeader("Retry-After", Long.toString(retryAfter));
        Problems.write(response, HttpStatus.TOO_MANY_REQUESTS, "Too many requests");
        return;
      }
    }
    if (!registration) {
      chain.doFilter(request, response);
      return;
    }
    String origin = request.getHeader("Origin");
    if (origin != null && !allowedOrigins.contains(origin)) {
      Problems.write(response, HttpStatus.FORBIDDEN, "Origin not allowed");
      return;
    }
    if (request.getContentLengthLong() > MAX_BODY_BYTES) {
      Problems.write(response, HttpStatus.PAYLOAD_TOO_LARGE, "Request body too large");
      return;
    }
    chain.doFilter(new LimitedBodyRequest(request), response);
  }

  /** Fails reading once more than {@link #MAX_BODY_BYTES} bytes arrive (chunked bodies). */
  @SuppressWarnings("PMD.CloseResource") // the servlet container owns and closes the stream
  private static final class LimitedBodyRequest extends HttpServletRequestWrapper {

    LimitedBodyRequest(HttpServletRequest request) {
      super(request);
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
      ServletInputStream in = super.getInputStream();
      return new ServletInputStream() {
        private long count;

        @Override
        public int read() throws IOException {
          int b = in.read();
          if (b >= 0) {
            count++;
            if (count > MAX_BODY_BYTES) {
              throw new PayloadTooLargeException();
            }
          }
          return b;
        }

        @Override
        public int read(byte[] buf, int off, int len) throws IOException {
          int n = in.read(buf, off, len);
          if (n > 0) {
            count += n;
            if (count > MAX_BODY_BYTES) {
              throw new PayloadTooLargeException();
            }
          }
          return n;
        }

        @Override
        public boolean isFinished() {
          return in.isFinished();
        }

        @Override
        public boolean isReady() {
          return in.isReady();
        }

        @Override
        public void setReadListener(ReadListener listener) {
          in.setReadListener(listener);
        }
      };
    }
  }
}

package si.konferenca.registration.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Refuses request bodies above the configured size (SR-03): by Content-Length before reading, and
 * while reading for bodies without a length.
 */
public class RequestSizeLimitFilter extends OncePerRequestFilter {

  private final long maxBytes;

  public RequestSizeLimitFilter(long maxBytes) {
    this.maxBytes = maxBytes;
  }

  /** Thrown while reading a body that is too large. */
  static final class TooLargeException extends IOException {
    private static final long serialVersionUID = 1L;

    TooLargeException() {
      super("request body too large");
    }
  }

  static boolean isTooLarge(Throwable error) {
    for (Throwable t = error; t != null; t = t.getCause()) {
      if (t instanceof TooLargeException) {
        return true;
      }
    }
    return false;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (request.getContentLengthLong() > maxBytes) {
      response.setStatus(HttpStatus.CONTENT_TOO_LARGE.value());
      response.setContentType(Problems.PROBLEM_JSON.toString());
      response
          .getOutputStream()
          .write(
              Problems.json(
                      HttpStatus.CONTENT_TOO_LARGE,
                      "Request too large",
                      "The request body is too large.")
                  .getBytes(StandardCharsets.UTF_8));
      return;
    }
    chain.doFilter(new LimitedRequest(request, maxBytes), response);
  }

  /** Bytes still allowed for one request, shared by every stream of that request. */
  private static final class Budget {
    private long remaining;

    Budget(long maxBytes) {
      this.remaining = maxBytes;
    }

    synchronized void consume(int n) throws TooLargeException {
      remaining -= n;
      if (remaining < 0) {
        throw new TooLargeException();
      }
    }
  }

  private static final class LimitedRequest extends HttpServletRequestWrapper {
    private final Budget budget;

    LimitedRequest(HttpServletRequest request, long maxBytes) {
      super(request);
      this.budget = new Budget(maxBytes);
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
      return new LimitedStream(super.getInputStream(), budget);
    }
  }

  private static final class LimitedStream extends ServletInputStream {
    private final ServletInputStream delegate;
    private final Budget budget;

    LimitedStream(ServletInputStream delegate, Budget budget) {
      this.delegate = delegate;
      this.budget = budget;
    }

    private int count(int n) throws TooLargeException {
      if (n > 0) {
        budget.consume(n);
      }
      return n;
    }

    @Override
    public int read() throws IOException {
      int b = delegate.read();
      if (b >= 0) {
        count(1);
      }
      return b;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
      return count(delegate.read(buffer, offset, length));
    }

    @Override
    public boolean isFinished() {
      return delegate.isFinished();
    }

    @Override
    public boolean isReady() {
      return delegate.isReady();
    }

    @Override
    public void setReadListener(ReadListener listener) {
      delegate.setReadListener(listener);
    }
  }
}

package si.konferenca.registration.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Limits request bodies under /api (SR-03): a declared length above the limit is refused at once;
 * an undeclared one fails while it is read.
 */
public class RequestSizeFilter extends OncePerRequestFilter {

  /** The body exceeded the configured limit. */
  public static final class PayloadTooLargeException extends IOException {
    private static final long serialVersionUID = 1L;

    PayloadTooLargeException() {
      super("Request body too large");
    }
  }

  private final long maxBytes;

  public RequestSizeFilter(long maxBytes) {
    this.maxBytes = maxBytes;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (request.getContentLengthLong() > maxBytes) {
      ErrorResponseWriter.write(
          response,
          HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
          ErrorResponse.of("payload_too_large", "The request is too large."));
      return;
    }
    chain.doFilter(new LimitedRequest(request, maxBytes), response);
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getRequestURI().startsWith("/api/");
  }

  private static final class LimitedRequest extends HttpServletRequestWrapper {
    private final long maxBytes;
    private ServletInputStream stream;

    LimitedRequest(HttpServletRequest request, long maxBytes) {
      super(request);
      this.maxBytes = maxBytes;
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
      if (stream == null) {
        stream = new LimitedStream(super.getInputStream(), maxBytes);
      }
      return stream;
    }
  }

  private static final class LimitedStream extends ServletInputStream {
    private final ServletInputStream delegate;
    private final long maxBytes;
    private long read;

    LimitedStream(ServletInputStream delegate, long maxBytes) {
      this.delegate = delegate;
      this.maxBytes = maxBytes;
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
      int n = delegate.read(buffer, offset, length);
      if (n > 0) {
        count(n);
      }
      return n;
    }

    private void count(int n) throws PayloadTooLargeException {
      read += n;
      if (read > maxBytes) {
        throw new PayloadTooLargeException();
      }
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

package si.konferenca.registration.web.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;
import si.konferenca.registration.web.dto.ErrorResponse;

/**
 * Rejects API requests whose body exceeds the configured limit. Bodies with a declared length are
 * checked up front; bodies without a declared length are read through a bounded stream.
 */
public class RequestSizeLimitFilter extends OncePerRequestFilter {

  private final long maxBytes;
  private final ErrorResponseWriter errorWriter;

  public RequestSizeLimitFilter(long maxBytes, ErrorResponseWriter errorWriter) {
    this.maxBytes = maxBytes;
    this.errorWriter = errorWriter;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getRequestURI().startsWith("/api/");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (request.getContentLengthLong() > maxBytes) {
      errorWriter.write(
          response,
          HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
          ErrorResponse.of("PAYLOAD_TOO_LARGE", "The request is too large."));
      return;
    }
    chain.doFilter(new BoundedRequest(request, maxBytes), response);
  }

  private static final class BoundedRequest extends HttpServletRequestWrapper {
    private final long maxBytes;
    private ServletInputStream stream;

    BoundedRequest(HttpServletRequest request, long maxBytes) {
      super(request);
      this.maxBytes = maxBytes;
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
      if (stream == null) {
        stream = new BoundedInputStream(super.getInputStream(), maxBytes);
      }
      return stream;
    }
  }

  private static final class BoundedInputStream extends ServletInputStream {
    private final ServletInputStream delegate;
    private final long maxBytes;
    private long read;

    BoundedInputStream(ServletInputStream delegate, long maxBytes) {
      this.delegate = delegate;
      this.maxBytes = maxBytes;
    }

    @Override
    public int read() throws IOException {
      int value = delegate.read();
      if (value >= 0) {
        count(1);
      }
      return value;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
      int count = delegate.read(buffer, offset, length);
      if (count > 0) {
        count(count);
      }
      return count;
    }

    private void count(int bytes) throws PayloadTooLargeException {
      read += bytes;
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

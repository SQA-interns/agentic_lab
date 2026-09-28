package si.konferenca.registration.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import si.konferenca.registration.config.AppProperties;

/**
 * Rejects API request bodies larger than the configured limit: by Content-Length with 413, and by
 * failing the read when a body without Content-Length exceeds the limit.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestSizeLimitFilter extends OncePerRequestFilter {

  private final long maxBodyBytes;

  public RequestSizeLimitFilter(AppProperties properties) {
    this.maxBodyBytes = properties.request().maxBodyBytes();
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getRequestURI().startsWith("/api/");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (request.getContentLengthLong() > maxBodyBytes) {
      JsonErrorWriter.write(response, 413, "PAYLOAD_TOO_LARGE", "The request body is too large.");
      return;
    }
    chain.doFilter(new LimitedRequest(request, maxBodyBytes), response);
  }

  /** Request whose body stream fails once more than {@code limit} bytes are read. */
  private static final class LimitedRequest extends HttpServletRequestWrapper {

    private final long limit;
    private ServletInputStream stream;

    LimitedRequest(HttpServletRequest request, long limit) {
      super(request);
      this.limit = limit;
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
      if (stream == null) {
        stream = new LimitedInputStream(super.getInputStream(), limit);
      }
      return stream;
    }
  }

  /** Counting stream that throws after the limit is exceeded. */
  private static final class LimitedInputStream extends ServletInputStream {

    private final ServletInputStream delegate;
    private final long limit;
    private long count;

    LimitedInputStream(ServletInputStream delegate, long limit) {
      this.delegate = delegate;
      this.limit = limit;
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

    private void count(int n) throws IOException {
      count += n;
      if (count > limit) {
        throw new IOException("Request body exceeds the configured limit");
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

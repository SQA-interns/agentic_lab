package si.konferenca.registration.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import si.konferenca.registration.config.AppProperties;
import si.konferenca.registration.config.SecurityConfig;

/**
 * Rejects registration bodies larger than {@code MAX_REQUEST_BYTES} with 413 (SR-03), whether or
 * not the client declares a length.
 */
@Component
@Order(-120)
public class RequestSizeFilter extends OncePerRequestFilter {

  private final long maxBytes;

  public RequestSizeFilter(AppProperties properties) {
    this.maxBytes = properties.maxRequestBytes();
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !"POST".equals(request.getMethod());
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (request.getContentLengthLong() > maxBytes) {
      tooLarge(response);
      return;
    }
    byte[] body =
        request.getInputStream().readNBytes((int) Math.min(Integer.MAX_VALUE, maxBytes + 1));
    if (body.length > maxBytes) {
      tooLarge(response);
      return;
    }
    chain.doFilter(new CachedBodyRequest(request, body), response);
  }

  private static void tooLarge(HttpServletResponse response) throws IOException {
    SecurityConfig.writeProblem(response, 413, "Request too large");
  }

  /** The already read body, served again to the controller. */
  private static final class CachedBodyRequest extends HttpServletRequestWrapper {

    private final byte[] body;

    CachedBodyRequest(HttpServletRequest request, byte[] body) {
      super(request);
      this.body = body.clone();
    }

    @Override
    public ServletInputStream getInputStream() {
      ByteArrayInputStream in = new ByteArrayInputStream(body);
      return new ServletInputStream() {
        @Override
        public int read() {
          return in.read();
        }

        @Override
        public int read(byte[] b, int off, int len) {
          return in.read(b, off, len);
        }

        @Override
        public boolean isFinished() {
          return in.available() == 0;
        }

        @Override
        public boolean isReady() {
          return true;
        }

        @Override
        public void setReadListener(ReadListener listener) {
          throw new UnsupportedOperationException("synchronous reading only");
        }
      };
    }

    @Override
    public BufferedReader getReader() {
      return new BufferedReader(
          new InputStreamReader(new ByteArrayInputStream(body), StandardCharsets.UTF_8));
    }

    @Override
    public int getContentLength() {
      return body.length;
    }

    @Override
    public long getContentLengthLong() {
      return body.length;
    }
  }
}

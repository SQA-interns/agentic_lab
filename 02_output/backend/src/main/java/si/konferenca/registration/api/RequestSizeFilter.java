package si.konferenca.registration.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

/** Refuses request bodies above the configured size with 413 (SR-03), with or without length. */
public class RequestSizeFilter extends OncePerRequestFilter {

  private final long maxBytes;

  public RequestSizeFilter(long maxBytes) {
    this.maxBytes = maxBytes;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (!request.getRequestURI().startsWith("/api/")) {
      chain.doFilter(request, response);
      return;
    }
    if (request.getContentLengthLong() > maxBytes) {
      Problems.write(response, HttpStatus.CONTENT_TOO_LARGE);
      return;
    }
    byte[] body = request.getInputStream().readNBytes((int) maxBytes + 1);
    if (body.length > maxBytes) {
      Problems.write(response, HttpStatus.CONTENT_TOO_LARGE);
      return;
    }
    chain.doFilter(new BufferedRequest(request, body), response);
  }

  /** The request with its already-read body. */
  private static final class BufferedRequest extends HttpServletRequestWrapper {
    private final byte[] body;

    BufferedRequest(HttpServletRequest request, byte[] body) {
      super(request);
      this.body = body;
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
        public int read(byte[] buffer, int offset, int length) {
          return in.read(buffer, offset, length);
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
          throw new UnsupportedOperationException();
        }
      };
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

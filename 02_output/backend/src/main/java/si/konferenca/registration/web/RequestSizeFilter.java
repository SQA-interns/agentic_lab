package si.konferenca.registration.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

/** Refuses request bodies larger than the configured limit with 413 (SR-03). */
public class RequestSizeFilter extends OncePerRequestFilter {

  private final long maxBytes;

  public RequestSizeFilter(long maxBytes) {
    this.maxBytes = maxBytes;
  }

  /** Thrown while reading a streamed body beyond the limit. */
  static final class TooLargeException extends IOException {
    private static final long serialVersionUID = 1L;

    TooLargeException() {
      super("request body too large");
    }
  }

  static boolean isTooLarge(Throwable t) {
    for (Throwable c = t; c != null; c = c.getCause()) {
      if (c instanceof TooLargeException) {
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
      Problems.write(
          response, HttpStatus.CONTENT_TOO_LARGE, "PAYLOAD_TOO_LARGE", "The request is too large");
      return;
    }
    chain.doFilter(new LimitedRequest(request, maxBytes), response);
  }

  private static final class LimitedRequest extends HttpServletRequestWrapper {
    private final long max;
    private ServletInputStream stream;

    LimitedRequest(HttpServletRequest request, long max) {
      super(request);
      this.max = max;
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
      if (stream == null) {
        ServletInputStream in = super.getInputStream();
        stream =
            new ServletInputStream() {
              private long count;

              @Override
              public int read() throws IOException {
                int b = in.read();
                if (b >= 0 && ++count > max) {
                  throw new TooLargeException();
                }
                return b;
              }

              @Override
              public int read(byte[] buf, int off, int len) throws IOException {
                int n = in.read(buf, off, len);
                if (n > 0 && (count += n) > max) {
                  throw new TooLargeException();
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
      return stream;
    }
  }
}

package si.konferenca.registration.config;

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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rejects API request bodies larger than the limit with 413 (specification §8.1), whether or not a
 * Content-Length is sent: at most {@code limit + 1} bytes are ever read.
 */
public class RequestSizeLimitFilter extends OncePerRequestFilter {

  private final int limitBytes;

  public RequestSizeLimitFilter(int limitBytes) {
    this.limitBytes = limitBytes;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI().substring(request.getContextPath().length());
    return !path.startsWith("/api/");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (request.getContentLengthLong() > limitBytes) {
      reject(response);
      return;
    }
    byte[] body = readAtMost(request.getInputStream(), limitBytes + 1);
    if (body.length > limitBytes) {
      reject(response);
      return;
    }
    chain.doFilter(new CachedBodyRequest(request, body), response);
  }

  private static byte[] readAtMost(InputStream in, int max) throws IOException {
    return in.readNBytes(max);
  }

  private static void reject(HttpServletResponse response) throws IOException {
    ProblemResponses.write(
        response, 413, "Payload too large", "PAYLOAD_TOO_LARGE", "The request body is too large.");
  }

  /** Replays an already-read, size-checked body. */
  private static final class CachedBodyRequest extends HttpServletRequestWrapper {
    private final byte[] body;

    CachedBodyRequest(HttpServletRequest request, byte[] body) {
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
          throw new UnsupportedOperationException("asynchronous reads are not supported");
        }
      };
    }

    @Override
    public BufferedReader getReader() {
      return new BufferedReader(new InputStreamReader(getInputStream(), charset()));
    }

    private Charset charset() {
      String encoding = getCharacterEncoding();
      if (encoding == null) {
        return StandardCharsets.UTF_8;
      }
      try {
        return Charset.forName(encoding);
      } catch (IllegalArgumentException e) {
        return StandardCharsets.UTF_8;
      }
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

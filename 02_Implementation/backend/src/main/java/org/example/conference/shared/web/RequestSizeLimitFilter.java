package org.example.conference.shared.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.example.conference.shared.api.ErrorCode;
import org.example.conference.shared.api.Problems;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/** Rejects request bodies larger than the configured limit with 413. */
public class RequestSizeLimitFilter extends OncePerRequestFilter {

  private final long maxBytes;
  private final ObjectMapper objectMapper;

  public RequestSizeLimitFilter(long maxBytes, ObjectMapper objectMapper) {
    super();
    this.maxBytes = maxBytes;
    this.objectMapper = objectMapper;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    long length = request.getContentLengthLong();
    if (length > maxBytes) {
      response.setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
      response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
      objectMapper.writeValue(
          response.getOutputStream(),
          Problems.of(HttpStatus.PAYLOAD_TOO_LARGE, ErrorCode.PAYLOAD_TOO_LARGE, List.of()));
      return;
    }
    chain.doFilter(length < 0 ? new LimitedRequest(request, maxBytes) : request, response);
  }

  /** Bounds bodies sent without Content-Length (chunked). */
  static final class LimitedRequest extends HttpServletRequestWrapper {
    private final long maxBytes;

    LimitedRequest(HttpServletRequest request, long maxBytes) {
      super(request);
      this.maxBytes = maxBytes;
    }

    // The container owns and closes the underlying stream.
    @SuppressWarnings("PMD.CloseResource")
    @Override
    public ServletInputStream getInputStream() throws IOException {
      ServletInputStream delegate = super.getInputStream();
      return new ServletInputStream() {
        private long count;

        @Override
        public int read() throws IOException {
          int b = delegate.read();
          if (b >= 0) {
            count++;
            if (count > maxBytes) {
              throw new IOException("Request body exceeds limit");
            }
          }
          return b;
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
      };
    }
  }
}

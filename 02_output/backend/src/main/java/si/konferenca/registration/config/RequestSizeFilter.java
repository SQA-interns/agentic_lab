package si.konferenca.registration.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Request-size limit (SR-03): a body larger than the limit gets 413; a body without a declared
 * length gets 411, so the limit cannot be bypassed with chunked encoding.
 */
class RequestSizeFilter extends OncePerRequestFilter {

  private final long maxBytes;

  RequestSizeFilter(long maxBytes) {
    this.maxBytes = maxBytes;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (hasBody(request)) {
      long length = request.getContentLengthLong();
      if (length < 0) {
        JsonErrors.write(response, 411, "The request must declare its length.");
        return;
      }
      if (length > maxBytes) {
        JsonErrors.write(response, 413, "The request is too large.");
        return;
      }
    }
    chain.doFilter(request, response);
  }

  private static boolean hasBody(HttpServletRequest request) {
    String method = request.getMethod();
    return "POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method);
  }
}

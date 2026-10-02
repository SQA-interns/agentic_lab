package si.konferenca.registration.adapter.in.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Refuses organizer requests that did not arrive over HTTPS, before any credentials are read and
 * without asking for them (SR-06).
 */
public class HttpsRequiredFilter extends OncePerRequestFilter {

  private final boolean requireHttps;
  private final ClientRequests clientRequests;

  public HttpsRequiredFilter(boolean requireHttps, ClientRequests clientRequests) {
    this.requireHttps = requireHttps;
    this.clientRequests = clientRequests;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (requireHttps
        && ExportController.PATH.equals(request.getRequestURI())
        && !clientRequests.isHttps(request)) {
      ProblemWriter.write(response, HttpStatus.FORBIDDEN.value());
      return;
    }
    chain.doFilter(request, response);
  }
}

package si.konferenca.registration.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Refuses organizer requests that did not arrive over HTTPS, before credentials are read (SR-06,
 * SB-04). Behind the reverse proxy, isSecure() reflects X-Forwarded-Proto from trusted proxies.
 */
final class HttpsOnlyFilter extends OncePerRequestFilter {

  private final boolean httpsOnly;

  HttpsOnlyFilter(boolean httpsOnly) {
    this.httpsOnly = httpsOnly;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !httpsOnly || !request.getRequestURI().startsWith("/api/export/");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (!request.isSecure()) {
      ErrorResponses.write(response, 403, "HTTPS_REQUIRED", ErrorResponses.HTTPS_REQUIRED);
      return;
    }
    chain.doFilter(request, response);
  }
}

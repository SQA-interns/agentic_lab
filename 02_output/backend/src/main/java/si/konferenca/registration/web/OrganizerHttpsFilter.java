package si.konferenca.registration.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Refuses organizer requests that did not arrive over HTTPS, before any credential is checked
 * (SR-06, AC-008-03). "Secure" is decided by the servlet container, which trusts X-Forwarded-Proto
 * only from internal proxy addresses.
 */
public class OrganizerHttpsFilter extends OncePerRequestFilter {

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getRequestURI().startsWith("/api/organizer/");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (!request.isSecure()) {
      ErrorResponses.write(
          response, ErrorBody.of(403, "forbidden", "Organizer access requires HTTPS."));
      return;
    }
    chain.doFilter(request, response);
  }
}

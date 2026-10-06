package si.konferenca.registration.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;
import si.konferenca.registration.web.Problems;

/**
 * SR-06: with HTTPS-only organizer access on, the export over plain HTTP is refused with 403 before
 * any credentials are looked at.
 */
class OrganizerHttpsFilter extends OncePerRequestFilter {

  static final String EXPORT_PATH = "/api/registrations/export";

  private final boolean httpsOnly;

  OrganizerHttpsFilter(boolean httpsOnly) {
    this.httpsOnly = httpsOnly;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (httpsOnly && EXPORT_PATH.equals(request.getRequestURI()) && !request.isSecure()) {
      response.setStatus(HttpStatus.FORBIDDEN.value());
      response.setContentType(Problems.PROBLEM_JSON.toString());
      response
          .getOutputStream()
          .write(
              Problems.json(
                      HttpStatus.FORBIDDEN,
                      "HTTPS required",
                      "Organizer access is only possible over HTTPS.")
                  .getBytes(StandardCharsets.UTF_8));
      return;
    }
    chain.doFilter(request, response);
  }
}

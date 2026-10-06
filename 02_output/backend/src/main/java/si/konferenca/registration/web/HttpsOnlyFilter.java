package si.konferenca.registration.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Refuses organizer requests over plain HTTP from non-local clients before credentials are read
 * (SR-06). Behind the trusted proxy, isSecure() reflects X-Forwarded-Proto.
 */
public class HttpsOnlyFilter extends OncePerRequestFilter {

  private final boolean httpsOnly;

  public HttpsOnlyFilter(boolean httpsOnly) {
    this.httpsOnly = httpsOnly;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getRequestURI().startsWith("/api/admin/");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (httpsOnly && !request.isSecure() && !isLoopback(request.getRemoteAddr())) {
      Problems.write(
          response, HttpStatus.FORBIDDEN, "HTTPS_REQUIRED", "Organizer access requires HTTPS");
      return;
    }
    chain.doFilter(request, response);
  }

  static boolean isLoopback(String address) {
    return address != null
        && (address.startsWith("127.")
            || "::1".equals(address)
            || "0:0:0:0:0:0:0:1".equals(address));
  }
}

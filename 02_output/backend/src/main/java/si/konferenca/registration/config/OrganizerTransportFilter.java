package si.konferenca.registration.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * SR-06: organizer requests over plain HTTP are refused before credentials are processed, except
 * from the local machine. "Secure" is what the servlet container reports after trusted-proxy
 * processing of {@code X-Forwarded-Proto}.
 */
class OrganizerTransportFilter extends OncePerRequestFilter {

  private final boolean httpsOnly;

  OrganizerTransportFilter(boolean httpsOnly) {
    this.httpsOnly = httpsOnly;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (httpsOnly
        && request.getRequestURI().startsWith("/api/admin/")
        && !request.isSecure()
        && !isLoopback(request.getRemoteAddr())) {
      JsonErrors.write(response, 403, "Organizer access requires HTTPS.");
      return;
    }
    chain.doFilter(request, response);
  }

  static boolean isLoopback(String address) {
    if (address == null || address.isBlank()) {
      return false;
    }
    try {
      // Literal addresses only: getByName does not resolve a numeric IP through DNS.
      return isIpLiteral(address) && InetAddress.getByName(address).isLoopbackAddress();
    } catch (UnknownHostException e) {
      return false;
    }
  }

  private static boolean isIpLiteral(String address) {
    return address.chars().allMatch(c -> Character.digit(c, 16) >= 0 || c == '.' || c == ':');
  }
}

package si.konferenca.registration.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * SR-06: organizer credentials are accepted only over HTTPS (as reported by a trusted proxy) or
 * from the local machine. Runs before authentication, so credentials sent over plain HTTP are never
 * checked.
 */
public class OrganizerHttpsFilter extends OncePerRequestFilter {

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !"/api/registrations/export".equals(request.getRequestURI());
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (request.isSecure() || isLoopback(request.getRemoteAddr())) {
      chain.doFilter(request, response);
      return;
    }
    Problems.write(response, HttpStatus.FORBIDDEN);
  }

  private static boolean isLoopback(String address) {
    // The remote address is always an IP literal, so no name lookup happens.
    try {
      return address != null && InetAddress.getByName(address).isLoopbackAddress();
    } catch (UnknownHostException e) {
      return false;
    }
  }
}

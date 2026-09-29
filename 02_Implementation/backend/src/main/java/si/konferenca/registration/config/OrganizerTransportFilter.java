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
 * Refuses organizer requests that did not arrive over HTTPS (specification §8.4), so HTTP Basic
 * credentials are never accepted over plaintext. Behind the reverse proxy {@link
 * HttpServletRequest#isSecure()} reflects {@code X-Forwarded-Proto}. Loopback clients are exempt:
 * their traffic never leaves the host (local tools, tests).
 */
public class OrganizerTransportFilter extends OncePerRequestFilter {

  private final boolean requireHttps;

  public OrganizerTransportFilter(boolean requireHttps) {
    this.requireHttps = requireHttps;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI().substring(request.getContextPath().length());
    return !requireHttps || !path.startsWith("/api/organizer/");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (request.isSecure() || isLoopback(request.getRemoteAddr())) {
      chain.doFilter(request, response);
      return;
    }
    ProblemResponses.write(
        response, 403, "HTTPS required", "HTTPS_REQUIRED", "Organizer endpoints require HTTPS.");
  }

  static boolean isLoopback(String address) {
    if (address == null || address.isBlank() || !address.matches("[0-9a-fA-F:.]+")) {
      return false;
    }
    try {
      // Literal IP addresses only (checked above), so no DNS lookup happens here.
      return InetAddress.getByName(address).isLoopbackAddress();
    } catch (UnknownHostException e) {
      return false;
    }
  }
}

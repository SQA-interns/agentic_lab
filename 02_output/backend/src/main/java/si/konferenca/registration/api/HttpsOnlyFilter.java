package si.konferenca.registration.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import si.konferenca.registration.config.AppProperties;
import si.konferenca.registration.config.SecurityConfig;

/**
 * Organizer credentials are accepted only over HTTPS or from localhost (SR-06). Runs before
 * authentication, so credentials sent over plain HTTP are never checked.
 */
@Component
@Order(-110)
public class HttpsOnlyFilter extends OncePerRequestFilter {

  private final boolean httpsOnly;

  public HttpsOnlyFilter(AppProperties properties) {
    this.httpsOnly = properties.organizer().httpsOnly();
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !httpsOnly || !"/api/export".equals(request.getRequestURI());
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (request.isSecure() || loopback(request.getRemoteAddr())) {
      chain.doFilter(request, response);
      return;
    }
    SecurityConfig.writeProblem(response, 403, "HTTPS is required for organizer access");
  }

  private static boolean loopback(String address) {
    try {
      // The servlet container supplies an IP literal, so no name lookup happens here.
      return address != null && InetAddress.getByName(address).isLoopbackAddress();
    } catch (UnknownHostException e) {
      return false;
    }
  }
}

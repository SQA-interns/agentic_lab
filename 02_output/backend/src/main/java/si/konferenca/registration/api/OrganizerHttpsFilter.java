package si.konferenca.registration.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Refuses the export over plain HTTP from anything but the local machine, before credentials are
 * read (SR-06). Behind the trusted reverse proxy, {@code X-Forwarded-Proto: https} makes the
 * request secure (server.forward-headers-strategy=native).
 */
public class OrganizerHttpsFilter extends OncePerRequestFilter {

  static final String EXPORT_PATH = "/api/export";

  private final boolean httpsOnly;

  public OrganizerHttpsFilter(boolean httpsOnly) {
    this.httpsOnly = httpsOnly;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (httpsOnly && !request.isSecure() && !isLoopback(request.getRemoteAddr())) {
      ErrorResponseWriter.write(
          response,
          HttpServletResponse.SC_FORBIDDEN,
          ErrorResponse.of("https_required", "Organizer access requires HTTPS."));
      return;
    }
    chain.doFilter(request, response);
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !EXPORT_PATH.equals(RequestPath.of(request));
  }

  static boolean isLoopback(String address) {
    if (address == null || address.isBlank() || !address.matches("[0-9a-fA-F:.]+")) {
      return false;
    }
    try {
      return InetAddress.getByName(address).isLoopbackAddress();
    } catch (UnknownHostException e) {
      return false;
    }
  }
}

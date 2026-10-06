package si.konferenca.registration.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Refuses organizer requests over plain HTTP from non-local clients before credentials are read
 * (SR-06). Behind the trusted proxy, isSecure() reflects X-Forwarded-Proto.
 */
public class HttpsOnlyFilter extends OncePerRequestFilter {

  private static final Pattern LITERAL_IP = Pattern.compile("^[0-9a-fA-F:.]+$");

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

  /** True for a literal loopback address; never resolves host names. */
  static boolean isLoopback(String address) {
    if (address == null || !LITERAL_IP.matcher(address).matches()) {
      return false;
    }
    try {
      return InetAddress.getByName(address).isLoopbackAddress();
    } catch (UnknownHostException e) {
      return false;
    }
  }
}

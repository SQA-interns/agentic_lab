package si.konferenca.registration.adapter.in.web;

import jakarta.servlet.http.HttpServletRequest;

/**
 * What is known about the client of a request. Forwarded headers are believed only when the setting
 * says the backend runs behind a trusted reverse proxy; otherwise a client could forge them.
 */
public final class ClientRequests {

  private final boolean trustForwardedHeaders;

  public ClientRequests(boolean trustForwardedHeaders) {
    this.trustForwardedHeaders = trustForwardedHeaders;
  }

  /** Whether the client reached the site over HTTPS. */
  public boolean isHttps(HttpServletRequest request) {
    if (request.isSecure()) {
      return true;
    }
    return trustForwardedHeaders
        && "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto"));
  }

  /** The address rate limits are counted for. */
  public String clientAddress(HttpServletRequest request) {
    if (trustForwardedHeaders) {
      String forwarded = request.getHeader("X-Forwarded-For");
      if (forwarded != null && !forwarded.isBlank()) {
        // The last entry is the one the trusted proxy added; earlier ones come from the client.
        String[] entries = forwarded.split(",");
        return entries[entries.length - 1].strip();
      }
    }
    return request.getRemoteAddr();
  }
}

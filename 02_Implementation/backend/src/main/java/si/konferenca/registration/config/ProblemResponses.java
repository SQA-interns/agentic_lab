package si.konferenca.registration.config;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Minimal RFC 9457 bodies for responses produced by servlet filters and security handlers, before a
 * controller is reached. The texts are constant, so no escaping is needed.
 */
final class ProblemResponses {

  private ProblemResponses() {}

  static void write(
      HttpServletResponse response, int status, String title, String code, String detail)
      throws IOException {
    response.setStatus(status);
    response.setContentType("application/problem+json");
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response
        .getWriter()
        .write(
            "{\"type\":\"about:blank\",\"title\":\""
                + title
                + "\",\"status\":"
                + status
                + ",\"code\":\""
                + code
                + "\",\"detail\":\""
                + detail
                + "\"}");
  }
}

package si.konferenca.registration.config;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Writes the contract's generic error body from servlet filters (no internals, ES-07). */
final class JsonErrors {

  private JsonErrors() {}

  static void write(HttpServletResponse response, int status, String message) throws IOException {
    response.setStatus(status);
    response.setContentType("application/json");
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.setHeader("X-Content-Type-Options", "nosniff");
    response.setHeader("Cache-Control", "no-store");
    // Messages are fixed strings from this package; no user input is echoed.
    response.getWriter().write("{\"message\":\"" + message + "\",\"errors\":[]}");
  }
}

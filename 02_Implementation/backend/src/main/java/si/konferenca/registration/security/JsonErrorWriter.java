package si.konferenca.registration.security;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;

/** Writes the uniform JSON error body from servlet filters (fixed, non-user-controlled text). */
final class JsonErrorWriter {

  private JsonErrorWriter() {}

  static void write(HttpServletResponse response, int status, String error, String message)
      throws IOException {
    response.setStatus(status);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.getWriter().write("{\"error\":\"" + error + "\",\"message\":\"" + message + "\"}");
  }
}

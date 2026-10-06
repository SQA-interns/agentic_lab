package si.konferenca.registration.web;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import tools.jackson.databind.json.JsonMapper;

/** Writes `Error` bodies from filters and security handlers, outside Spring MVC. */
final class ErrorResponses {

  static final String UNAUTHORIZED = "Organizer credentials are required.";
  static final String HTTPS_REQUIRED = "Organizer access requires HTTPS.";
  static final String RATE_LIMITED = "Too many requests. Please wait a moment and try again.";
  static final String PAYLOAD_TOO_LARGE = "The request is too large.";
  static final String INTERNAL_ERROR = "Something went wrong. Please try again later.";

  private static final JsonMapper MAPPER = JsonMapper.builder().build();

  private ErrorResponses() {}

  static void write(HttpServletResponse response, int status, String code, String message)
      throws IOException {
    response.setStatus(status);
    response.setContentType("application/json");
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.getOutputStream().write(MAPPER.writeValueAsBytes(ApiError.of(code, message)));
  }
}

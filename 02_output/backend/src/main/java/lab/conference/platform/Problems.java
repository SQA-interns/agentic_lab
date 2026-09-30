package lab.conference.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

/** Builds RFC 9457 problem bodies (application/problem+json). */
public final class Problems {

  private static final ObjectMapper JSON = new ObjectMapper();

  private Problems() {}

  public static Map<String, Object> body(HttpStatus status, String title, List<FieldError> errors) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("type", "about:blank");
    body.put("title", title);
    body.put("status", status.value());
    if (!errors.isEmpty()) {
      body.put("errors", errors);
    }
    return body;
  }

  /** Writes a problem response directly (used by servlet filters and security handlers). */
  public static void write(HttpServletResponse response, HttpStatus status, String title)
      throws IOException {
    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    JSON.writeValue(response.getOutputStream(), body(status, title, List.of()));
  }
}

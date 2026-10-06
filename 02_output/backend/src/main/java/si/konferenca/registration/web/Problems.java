package si.konferenca.registration.web;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import si.konferenca.registration.application.FieldError;
import tools.jackson.databind.json.JsonMapper;

/** RFC 9457 problem bodies with fixed texts only (openapi.yaml Problem, SB-07). */
public final class Problems {

  public static final MediaType PROBLEM_JSON = MediaType.APPLICATION_PROBLEM_JSON;
  private static final JsonMapper MAPPER = JsonMapper.builder().build();

  private Problems() {}

  public static Map<String, Object> body(HttpStatus status, String code, String title) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("type", "about:blank");
    body.put("title", title);
    body.put("status", status.value());
    body.put("code", code);
    return body;
  }

  public static ResponseEntity<Map<String, Object>> response(
      HttpStatus status, String code, String title) {
    return ResponseEntity.status(status).contentType(PROBLEM_JSON).body(body(status, code, title));
  }

  public static ResponseEntity<Map<String, Object>> validation(List<FieldError> errors) {
    Map<String, Object> body =
        body(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "The registration is not valid");
    body.put(
        "errors", errors.stream().map(e -> Map.of("field", e.field(), "code", e.code())).toList());
    return ResponseEntity.badRequest().contentType(PROBLEM_JSON).body(body);
  }

  /** Writes a problem directly, for filters running outside Spring MVC. */
  public static void write(
      HttpServletResponse response, HttpStatus status, String code, String title)
      throws IOException {
    response.setStatus(status.value());
    response.setContentType(PROBLEM_JSON.toString());
    response.setCharacterEncoding("UTF-8");
    response.getOutputStream().write(MAPPER.writeValueAsBytes(body(status, code, title)));
  }
}

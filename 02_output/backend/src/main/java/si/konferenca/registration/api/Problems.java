package si.konferenca.registration.api;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import si.konferenca.registration.domain.ValidationError;
import tools.jackson.databind.json.JsonMapper;

/** application/problem+json bodies with title, status and field codes only (SB-07, ES-07). */
public final class Problems {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  private Problems() {}

  public static Map<String, Object> body(HttpStatus status) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("type", "about:blank");
    body.put("title", status.getReasonPhrase());
    body.put("status", status.value());
    return body;
  }

  public static ResponseEntity<Map<String, Object>> of(HttpStatus status) {
    return ResponseEntity.status(status)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(body(status));
  }

  public static ResponseEntity<Map<String, Object>> validation(
      HttpStatus status, List<ValidationError> errors) {
    Map<String, Object> body = body(status);
    List<Map<String, Object>> list = new ArrayList<>();
    for (ValidationError error : errors) {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("field", error.field());
      item.put("code", error.code().name());
      if (error.consentId() != null) {
        item.put("consentId", error.consentId());
      }
      list.add(item);
    }
    body.put("errors", list);
    return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(body);
  }

  /** Writes a problem from a servlet filter, outside Spring MVC. */
  public static void write(HttpServletResponse response, HttpStatus status) throws IOException {
    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response.getOutputStream().write(JSON.writeValueAsBytes(body(status)));
  }
}

package si.konferenca.registration.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** RFC 9457 problem body of the REST contract; messages are fixed and safe to show (ES-07). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Problem(
    String type, String title, int status, String detail, String code, List<FieldError> errors) {

  /** One field error (field name of the request, error code). */
  public record FieldError(String field, String code) {}

  public Problem {
    errors = errors == null ? null : List.copyOf(errors);
  }

  public static Problem of(HttpStatus status, String code, String detail) {
    return new Problem("about:blank", status.getReasonPhrase(), status.value(), detail, code, null);
  }

  public static Problem validation(List<FieldError> errors) {
    HttpStatus status = HttpStatus.BAD_REQUEST;
    return new Problem(
        "about:blank",
        status.getReasonPhrase(),
        status.value(),
        "Some fields are missing or invalid.",
        "VALIDATION_FAILED",
        List.copyOf(errors));
  }

  public ResponseEntity<Problem> toResponse() {
    return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(this);
  }
}

package si.konferenca.registration.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import si.konferenca.registration.domain.FieldError;

/** RFC 9457 problem responses of the API contract, with fixed titles and no internals (ES-07). */
public final class Problems {

  public static final MediaType PROBLEM_JSON = MediaType.APPLICATION_PROBLEM_JSON;

  private Problems() {}

  /** A field error as the contract writes it. */
  public record FieldErrorBody(String field, String code, String message) {}

  /** The problem body. */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record ProblemBody(
      String type, String title, int status, String detail, List<FieldErrorBody> errors) {

    public ProblemBody {
      errors = errors == null ? null : List.copyOf(errors);
    }
  }

  public static ResponseEntity<ProblemBody> of(HttpStatus status, String title, String detail) {
    return of(status, title, detail, null);
  }

  public static ResponseEntity<ProblemBody> of(
      HttpStatus status, String title, String detail, List<FieldError> errors) {
    List<FieldErrorBody> body =
        errors == null
            ? null
            : errors.stream()
                .map(e -> new FieldErrorBody(e.field(), e.code().name(), e.message()))
                .toList();
    return ResponseEntity.status(status)
        .contentType(PROBLEM_JSON)
        .body(new ProblemBody("about:blank", title, status.value(), detail, body));
  }

  /** The same body as JSON text, for filters that write the response themselves. */
  public static String json(HttpStatus status, String title, String detail) {
    return "{\"type\":\"about:blank\",\"title\":\""
        + title
        + "\",\"status\":"
        + status.value()
        + ",\"detail\":\""
        + detail
        + "\"}";
  }
}

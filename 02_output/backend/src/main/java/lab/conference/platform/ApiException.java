package lab.conference.platform;

import java.util.List;
import org.springframework.http.HttpStatus;

/** An error that maps to an RFC 9457 problem response without internal details. */
public class ApiException extends RuntimeException {

  private static final long serialVersionUID = 1L;
  private static final String UNAVAILABLE =
      "Registration could not be stored. It was not accepted; please try again.";
  private final HttpStatus status;
  private final String title;
  private final List<FieldError> errors;

  public ApiException(HttpStatus status, String title, List<FieldError> errors, Throwable cause) {
    super(title, cause);
    this.status = status;
    this.title = title;
    this.errors = List.copyOf(errors);
  }

  public ApiException(HttpStatus status, String title, List<FieldError> errors) {
    this(status, title, errors, null);
  }

  public ApiException(HttpStatus status, String title) {
    this(status, title, List.of(), null);
  }

  public static ApiException validation(List<FieldError> errors) {
    return new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", errors);
  }

  public static ApiException conflict(String title) {
    return new ApiException(HttpStatus.CONFLICT, title);
  }

  public static ApiException unavailable() {
    return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, UNAVAILABLE);
  }

  /** Storage failure; the cause is kept for diagnostics but never sent to the client. */
  public static ApiException unavailable(Throwable cause) {
    return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, UNAVAILABLE, List.of(), cause);
  }

  public HttpStatus status() {
    return status;
  }

  public String title() {
    return title;
  }

  public List<FieldError> errors() {
    return errors;
  }
}

package lab.conference.platform;

import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Maps exceptions to problem responses; never exposes internal details (ES-07, SB-07). */
@RestControllerAdvice
public class ProblemHandler {

  private static final Logger LOG = LoggerFactory.getLogger(ProblemHandler.class);

  @ExceptionHandler(ApiException.class)
  public ResponseEntity<Map<String, Object>> api(ApiException e) {
    return respond(e.status(), e.title(), e.errors());
  }

  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  public ResponseEntity<Map<String, Object>> mediaType() {
    return respond(
        HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Content type must be application/json", List.of());
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<Map<String, Object>> unreadable(HttpMessageNotReadableException e) {
    for (Throwable t = e; t != null; t = t.getCause()) {
      if (t instanceof PayloadTooLargeException) {
        return tooLarge();
      }
    }
    return respond(
        HttpStatus.BAD_REQUEST,
        "Validation failed",
        List.of(
            new FieldError("body", "MALFORMED_REQUEST", "The request body is not valid JSON.")));
  }

  @ExceptionHandler(PayloadTooLargeException.class)
  public ResponseEntity<Map<String, Object>> tooLarge() {
    return respond(HttpStatus.PAYLOAD_TOO_LARGE, "Request body too large", List.of());
  }

  @ExceptionHandler({NoResourceFoundException.class, HttpRequestMethodNotSupportedException.class})
  public ResponseEntity<Map<String, Object>> notFound() {
    return respond(HttpStatus.NOT_FOUND, "Not found", List.of());
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Map<String, Object>> unexpected(Exception e) {
    LOG.error("Unexpected error ({})", e.getClass().getSimpleName());
    return respond(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error", List.of());
  }

  private static ResponseEntity<Map<String, Object>> respond(
      HttpStatus status, String title, List<FieldError> errors) {
    return ResponseEntity.status(status)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(Problems.body(status, title, errors));
  }
}

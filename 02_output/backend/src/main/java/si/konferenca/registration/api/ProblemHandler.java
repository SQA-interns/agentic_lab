package si.konferenca.registration.api;

import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Maps every error to an RFC 9457 problem with a fixed title (section 13). Exception messages,
 * classes and stack traces never reach the client (SB-07).
 */
@RestControllerAdvice
public class ProblemHandler extends ResponseEntityExceptionHandler {

  static final String PROCESSING_FAILED = "Registration could not be processed";
  private static final Logger LOG = LoggerFactory.getLogger(ProblemHandler.class);

  @ExceptionHandler(Exception.class)
  ResponseEntity<ProblemDetail> unexpected(Exception e) {
    LOG.error("Unexpected error: {}", e.getClass().getName());
    return problem(HttpStatus.INTERNAL_SERVER_ERROR, "about:blank", PROCESSING_FAILED);
  }

  /** Standard Spring MVC errors (415, 405, 404, unreadable body, ...) keep only their status. */
  @Override
  protected ResponseEntity<Object> handleExceptionInternal(
      Exception ex, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    ProblemDetail detail = ProblemDetail.forStatus(status);
    detail.setType(URI.create("about:blank"));
    detail.setTitle(titleFor(status));
    return ResponseEntity.status(status).headers(headers).body(detail);
  }

  static ResponseEntity<ProblemDetail> problem(HttpStatus status, String type, String title) {
    ProblemDetail detail = ProblemDetail.forStatus(status);
    detail.setType(URI.create(type));
    detail.setTitle(title);
    return ResponseEntity.status(status).body(detail);
  }

  private static String titleFor(HttpStatusCode status) {
    HttpStatus known = HttpStatus.resolve(status.value());
    return known == null ? "Error" : known.getReasonPhrase();
  }
}

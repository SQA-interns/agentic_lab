package si.konferenca.registration.web;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import si.konferenca.registration.application.DuplicateRegistrationException;
import si.konferenca.registration.application.ValidationException;

/** Maps every exception to the Error schema without internal details (ES-07, SB-07). */
@RestControllerAdvice
public class ApiExceptionHandler {

  static final String GENERIC_MESSAGE = "An unexpected error occurred. Please try again later.";

  private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

  @ExceptionHandler(ValidationException.class)
  ResponseEntity<ErrorBody> invalid(ValidationException e) {
    return ResponseEntity.badRequest()
        .body(
            new ErrorBody(
                400,
                "validation_failed",
                "Please correct the marked fields.",
                e.violations().stream()
                    .map(v -> new ErrorBody.FieldErrorBody(v.field(), v.message()))
                    .toList()));
  }

  @ExceptionHandler(DuplicateRegistrationException.class)
  ResponseEntity<ErrorBody> duplicate() {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(
            new ErrorBody(
                409,
                "duplicate_registration",
                "A registration with this email address already exists.",
                List.of(
                    new ErrorBody.FieldErrorBody(
                        "email", "A registration with this email address already exists."))));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<ErrorBody> unreadable(HttpMessageNotReadableException e) {
    return ResponseEntity.badRequest()
        .body(ErrorBody.of(400, "validation_failed", "The request body is not valid."));
  }

  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  ResponseEntity<ErrorBody> mediaType() {
    return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
        .body(ErrorBody.of(415, "unsupported_media_type", "Send the request as application/json."));
  }

  @ExceptionHandler({NoResourceFoundException.class, HttpRequestMethodNotSupportedException.class})
  ResponseEntity<ErrorBody> notFound() {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(ErrorBody.of(404, "not_found", "Not found."));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ErrorBody> unexpected(Exception e) {
    LOG.error("Unexpected error ({})", e.getClass().getSimpleName(), e);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(ErrorBody.of(500, "internal_error", GENERIC_MESSAGE));
  }
}

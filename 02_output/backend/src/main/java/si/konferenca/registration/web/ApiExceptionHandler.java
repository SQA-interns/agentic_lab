package si.konferenca.registration.web;

import java.util.Map;
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
import si.konferenca.registration.application.DuplicateEmailException;
import si.konferenca.registration.application.MalformedRequestException;
import si.konferenca.registration.application.ServiceUnavailableException;
import si.konferenca.registration.application.ValidationException;

/** Maps failures to problem responses without internal details (spec section 7, SB-07). */
@RestControllerAdvice
public class ApiExceptionHandler {

  private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

  @ExceptionHandler(ValidationException.class)
  ResponseEntity<Map<String, Object>> validation(ValidationException e) {
    return Problems.validation(e.errors());
  }

  @ExceptionHandler(DuplicateEmailException.class)
  ResponseEntity<Map<String, Object>> duplicate() {
    return Problems.response(
        HttpStatus.CONFLICT, "DUPLICATE_EMAIL", "This email address is already registered");
  }

  @ExceptionHandler({MalformedRequestException.class, HttpMessageNotReadableException.class})
  ResponseEntity<Map<String, Object>> malformed(Exception e) {
    if (RequestSizeFilter.isTooLarge(e)) {
      return Problems.response(
          HttpStatus.CONTENT_TOO_LARGE, "PAYLOAD_TOO_LARGE", "The request is too large");
    }
    return Problems.response(
        HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "The request could not be read");
  }

  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  ResponseEntity<Map<String, Object>> mediaType() {
    return Problems.response(
        HttpStatus.UNSUPPORTED_MEDIA_TYPE,
        "UNSUPPORTED_MEDIA_TYPE",
        "The request must be application/json");
  }

  @ExceptionHandler({HttpRequestMethodNotSupportedException.class, NoResourceFoundException.class})
  ResponseEntity<Map<String, Object>> notFound() {
    return Problems.response(HttpStatus.NOT_FOUND, "NOT_FOUND", "Not found");
  }

  @ExceptionHandler(ServiceUnavailableException.class)
  ResponseEntity<Map<String, Object>> unavailable() {
    return Problems.response(
        HttpStatus.SERVICE_UNAVAILABLE,
        "SERVICE_UNAVAILABLE",
        "The registration could not be processed, please try again later");
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<Map<String, Object>> unexpected(Exception e) {
    LOG.error("Unexpected error: {}", e.getClass().getName());
    return Problems.response(
        HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred");
  }
}

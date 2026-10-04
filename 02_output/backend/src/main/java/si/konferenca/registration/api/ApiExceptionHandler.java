package si.konferenca.registration.api;

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

/** Maps failures to the problem bodies of the REST contract, never exposing internals. */
@RestControllerAdvice
public class ApiExceptionHandler {

  private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<Problem> malformed(HttpMessageNotReadableException e) {
    return Problem.of(
            HttpStatus.BAD_REQUEST,
            "MALFORMED_REQUEST",
            "The request body is not valid JSON for this operation.")
        .toResponse();
  }

  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  ResponseEntity<Problem> unsupported(HttpMediaTypeNotSupportedException e) {
    return Problem.of(
            HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", "Send application/json.")
        .toResponse();
  }

  @ExceptionHandler({HttpRequestMethodNotSupportedException.class, NoResourceFoundException.class})
  ResponseEntity<Problem> notFound(Exception e) {
    return Problem.of(HttpStatus.NOT_FOUND, "INTERNAL_ERROR", "Not found.").toResponse();
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<Problem> unexpected(Exception e) {
    LOG.error("unexpected error: {}", e.getClass().getName());
    return Problem.of(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "INTERNAL_ERROR",
            "Something went wrong. Please try again later.")
        .toResponse();
  }
}

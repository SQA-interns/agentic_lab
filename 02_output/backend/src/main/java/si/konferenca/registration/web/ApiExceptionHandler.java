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
import si.konferenca.registration.domain.ErrorCode;
import si.konferenca.registration.domain.FieldError;

/** Maps every error to a problem response without internals (docs/02_specification.md §10). */
@RestControllerAdvice
public class ApiExceptionHandler {

  private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<Problems.ProblemBody> unreadable(HttpMessageNotReadableException e) {
    if (RequestSizeLimitFilter.isTooLarge(e)) {
      return Problems.of(
          HttpStatus.CONTENT_TOO_LARGE, "Request too large", "The request body is too large.");
    }
    return Problems.of(
        HttpStatus.BAD_REQUEST,
        "Malformed request",
        "The request could not be read.",
        List.of(FieldError.of("body", ErrorCode.MALFORMED)));
  }

  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  ResponseEntity<Problems.ProblemBody> mediaType() {
    return Problems.of(
        HttpStatus.UNSUPPORTED_MEDIA_TYPE,
        "Unsupported media type",
        "Send the request as application/json.");
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  ResponseEntity<Problems.ProblemBody> method() {
    return Problems.of(
        HttpStatus.METHOD_NOT_ALLOWED, "Method not allowed", "This method is not supported.");
  }

  @ExceptionHandler(NoResourceFoundException.class)
  ResponseEntity<Problems.ProblemBody> notFound() {
    return Problems.of(HttpStatus.NOT_FOUND, "Not found", "There is nothing at this address.");
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<Problems.ProblemBody> unexpected(Exception e) {
    LOG.error("Unexpected error while handling a request", e);
    return Problems.of(
        HttpStatus.INTERNAL_SERVER_ERROR, "Internal error", "Please try again later.");
  }
}

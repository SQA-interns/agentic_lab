package si.konferenca.registration.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import si.konferenca.registration.service.RegistrationRejectedException;

/** Maps failures to the `Error` body of `openapi.yaml`, without internal details (ES-07). */
@RestControllerAdvice
public class ApiExceptionHandler {

  private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

  @ExceptionHandler(RegistrationRejectedException.class)
  ResponseEntity<ApiError> rejected(RegistrationRejectedException e) {
    var fieldErrors =
        e.fieldErrors().isEmpty()
            ? null
            : e.fieldErrors().stream()
                .map(
                    f ->
                        new ApiError.FieldErrorBody(f.field(), f.code().name(), f.code().message()))
                .toList();
    return ResponseEntity.status(e.code().status())
        .body(new ApiError(e.code().name(), e.code().message(), fieldErrors));
  }

  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  ResponseEntity<ApiError> unsupportedMediaType() {
    return ResponseEntity.status(415)
        .body(ApiError.of("UNSUPPORTED_MEDIA_TYPE", "The request must be sent as JSON."));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ApiError> unexpected(Exception e) {
    if (e instanceof ErrorResponse framework && framework.getStatusCode().is4xxClientError()) {
      return ResponseEntity.status(framework.getStatusCode())
          .body(ApiError.of("MALFORMED_REQUEST", "The request could not be read."));
    }
    LOG.error("unexpected error ({})", e.getClass().getName());
    return ResponseEntity.status(500)
        .body(ApiError.of("INTERNAL_ERROR", ErrorResponses.INTERNAL_ERROR));
  }
}

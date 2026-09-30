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
import si.konferenca.registration.api.Dtos.ErrorResponse;
import si.konferenca.registration.api.Dtos.FieldErrorDto;
import si.konferenca.registration.application.RegistrationRejectedException;
import si.konferenca.registration.application.StorageException;

/** Maps failures to the contract's error body without internal details (ES-07, SB-07). */
@RestControllerAdvice
class ApiExceptionHandler {

  private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

  @ExceptionHandler(RegistrationRejectedException.class)
  ResponseEntity<ErrorResponse> rejected(RegistrationRejectedException e) {
    return ResponseEntity.badRequest()
        .body(
            new ErrorResponse(
                "Please correct the marked fields.",
                e.errors().stream().map(FieldErrorDto::of).toList()));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<ErrorResponse> unreadable(HttpMessageNotReadableException e) {
    return ResponseEntity.badRequest().body(ErrorResponse.of("The request is not valid."));
  }

  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  ResponseEntity<ErrorResponse> mediaType(HttpMediaTypeNotSupportedException e) {
    return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
        .body(ErrorResponse.of("Send the request as JSON."));
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  ResponseEntity<ErrorResponse> method(HttpRequestMethodNotSupportedException e) {
    return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
        .body(ErrorResponse.of("Method not allowed."));
  }

  @ExceptionHandler(NoResourceFoundException.class)
  ResponseEntity<ErrorResponse> notFound(NoResourceFoundException e) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse.of("Not found."));
  }

  @ExceptionHandler(StorageException.class)
  ResponseEntity<ErrorResponse> storage(StorageException e) {
    LOG.error("A registration could not be stored and was rolled back", e);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(ErrorResponse.of("Registration could not be saved. Please try again."));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ErrorResponse> unexpected(Exception e) {
    LOG.error("Unexpected error", e);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(ErrorResponse.of("An unexpected error occurred. Please try again later."));
  }
}

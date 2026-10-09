package si.konferenca.registration.api;

import java.util.List;
import java.util.UUID;
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
import si.konferenca.registration.application.CaptchaVerifier;
import si.konferenca.registration.application.DuplicateEmailException;
import si.konferenca.registration.application.ValidationException;

/**
 * Maps every failure to an {@link ErrorResponse} without internal details (ES-07, SB-07);
 * unexpected failures are logged with an error id and the exception type only.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

  static final String GENERIC_MESSAGE =
      "The registration could not be processed. Please try again later.";

  private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

  @ExceptionHandler(ValidationException.class)
  ResponseEntity<ErrorResponse> validation(ValidationException e) {
    List<ErrorResponse.FieldErrorResponse> fields =
        e.errors().stream()
            .map(
                error ->
                    new ErrorResponse.FieldErrorResponse(
                        error.field(), error.code(), error.message()))
            .toList();
    return respond(
        HttpStatus.BAD_REQUEST,
        new ErrorResponse("validation_failed", "Some fields are invalid.", fields));
  }

  @ExceptionHandler(RegistrationController.InvalidRequestException.class)
  ResponseEntity<ErrorResponse> invalidRequest(RegistrationController.InvalidRequestException e) {
    return respond(HttpStatus.BAD_REQUEST, ErrorResponse.of("invalid_request", e.getMessage()));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<ErrorResponse> unreadable(HttpMessageNotReadableException e) {
    for (Throwable t = e; t != null; t = t.getCause()) {
      if (t instanceof RequestSizeFilter.PayloadTooLargeException) {
        return payloadTooLarge();
      }
    }
    return respond(
        HttpStatus.BAD_REQUEST,
        ErrorResponse.of("invalid_request", "The request body is not a valid registration."));
  }

  @ExceptionHandler(RequestSizeFilter.PayloadTooLargeException.class)
  ResponseEntity<ErrorResponse> tooLarge(RequestSizeFilter.PayloadTooLargeException e) {
    return payloadTooLarge();
  }

  @ExceptionHandler(CaptchaVerifier.CaptchaFailedException.class)
  ResponseEntity<ErrorResponse> captchaFailed(CaptchaVerifier.CaptchaFailedException e) {
    return respond(
        HttpStatus.BAD_REQUEST,
        ErrorResponse.of("captcha_failed", "Please confirm that you are not a robot."));
  }

  @ExceptionHandler(CaptchaVerifier.CaptchaUnavailableException.class)
  ResponseEntity<ErrorResponse> captchaUnavailable(CaptchaVerifier.CaptchaUnavailableException e) {
    LOG.warn("Anti-automation verification unavailable ({})", rootType(e));
    return respond(
        HttpStatus.SERVICE_UNAVAILABLE,
        ErrorResponse.of(
            "captcha_unavailable",
            "The anti-automation check is unavailable. Please try again later."));
  }

  @ExceptionHandler(DuplicateEmailException.class)
  ResponseEntity<ErrorResponse> duplicate(DuplicateEmailException e) {
    String message = "This email address is already registered.";
    return respond(
        HttpStatus.CONFLICT,
        new ErrorResponse(
            "duplicate_email",
            message,
            List.of(new ErrorResponse.FieldErrorResponse("email", "duplicate_email", message))));
  }

  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  ResponseEntity<ErrorResponse> mediaType(HttpMediaTypeNotSupportedException e) {
    return respond(
        HttpStatus.UNSUPPORTED_MEDIA_TYPE,
        ErrorResponse.of("unsupported_media_type", "Send the registration as application/json."));
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  ResponseEntity<ErrorResponse> method(HttpRequestMethodNotSupportedException e) {
    return respond(
        HttpStatus.METHOD_NOT_ALLOWED,
        ErrorResponse.of("invalid_request", "This method is not supported here."));
  }

  @ExceptionHandler(NoResourceFoundException.class)
  ResponseEntity<ErrorResponse> notFound(NoResourceFoundException e) {
    return respond(HttpStatus.NOT_FOUND, ErrorResponse.of("not_found", "Not found."));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ErrorResponse> unexpected(Exception e) {
    String errorId = UUID.randomUUID().toString();
    LOG.error("Unexpected failure {} ({}, root {})", errorId, e.getClass().getName(), rootType(e));
    return respond(
        HttpStatus.INTERNAL_SERVER_ERROR, ErrorResponse.of("internal_error", GENERIC_MESSAGE));
  }

  private static ResponseEntity<ErrorResponse> payloadTooLarge() {
    return respond(
        HttpStatus.PAYLOAD_TOO_LARGE,
        ErrorResponse.of("payload_too_large", "The request is too large."));
  }

  private static ResponseEntity<ErrorResponse> respond(HttpStatus status, ErrorResponse body) {
    return ResponseEntity.status(status).body(body);
  }

  private static String rootType(Throwable failure) {
    Throwable root = failure;
    while (root.getCause() != null && root.getCause() != root) {
      root = root.getCause();
    }
    return root.getClass().getName();
  }
}

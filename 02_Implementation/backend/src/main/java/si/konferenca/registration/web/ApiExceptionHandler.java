package si.konferenca.registration.web;

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
import si.konferenca.registration.application.CaptchaFailedException;
import si.konferenca.registration.application.CaptchaUnavailableException;
import si.konferenca.registration.application.RegistrationStorageException;
import si.konferenca.registration.application.ValidationException;
import si.konferenca.registration.web.dto.ErrorResponse;
import si.konferenca.registration.web.filter.PayloadTooLargeException;

/** Maps exceptions to the uniform error body without leaking internals. */
@RestControllerAdvice
public class ApiExceptionHandler {

  private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

  @ExceptionHandler(ValidationException.class)
  ResponseEntity<ErrorResponse> validation(ValidationException e) {
    return ResponseEntity.badRequest()
        .body(
            new ErrorResponse(
                "VALIDATION_FAILED", "Please correct the highlighted fields.", e.getViolations()));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<ErrorResponse> unreadable(HttpMessageNotReadableException e) {
    if (hasCause(e, PayloadTooLargeException.class)) {
      return error(HttpStatus.PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE", "The request is too large.");
    }
    return error(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "The request could not be read.");
  }

  @ExceptionHandler(CaptchaFailedException.class)
  ResponseEntity<ErrorResponse> captchaFailed() {
    return error(
        HttpStatus.BAD_REQUEST,
        "CAPTCHA_FAILED",
        "The anti-robot check failed. Please complete it again.");
  }

  @ExceptionHandler(CaptchaUnavailableException.class)
  ResponseEntity<ErrorResponse> captchaUnavailable(CaptchaUnavailableException e) {
    LOG.error("reCAPTCHA verification unavailable: {}", rootCauseName(e));
    return error(
        HttpStatus.SERVICE_UNAVAILABLE,
        "CAPTCHA_UNAVAILABLE",
        "The anti-robot check is temporarily unavailable. Please try again later.");
  }

  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  ResponseEntity<ErrorResponse> unsupportedMediaType() {
    return error(
        HttpStatus.UNSUPPORTED_MEDIA_TYPE,
        "UNSUPPORTED_MEDIA_TYPE",
        "The request content type is not supported.");
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  ResponseEntity<ErrorResponse> methodNotAllowed() {
    return error(
        HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "The request method is not allowed.");
  }

  @ExceptionHandler(NoResourceFoundException.class)
  ResponseEntity<ErrorResponse> notFound() {
    return error(HttpStatus.NOT_FOUND, "NOT_FOUND", "The requested resource does not exist.");
  }

  @ExceptionHandler(RegistrationStorageException.class)
  ResponseEntity<ErrorResponse> storageFailed(RegistrationStorageException e) {
    LOG.error("Registration could not be stored: {}", rootCauseName(e));
    return error(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "REGISTRATION_FAILED",
        "Your registration could not be completed. Please try again later.");
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ErrorResponse> unexpected(Exception e) {
    LOG.error("Unexpected error: {}", rootCauseName(e));
    return error(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "INTERNAL_ERROR",
        "An unexpected error occurred. Please try again later.");
  }

  private static ResponseEntity<ErrorResponse> error(
      HttpStatus status, String code, String message) {
    return ResponseEntity.status(status).body(ErrorResponse.of(code, message));
  }

  private static boolean hasCause(Throwable error, Class<? extends Throwable> type) {
    for (Throwable t = error; t != null; t = t.getCause()) {
      if (type.isInstance(t)) {
        return true;
      }
    }
    return false;
  }

  private static String rootCauseName(Throwable error) {
    Throwable root = error;
    while (root.getCause() != null && root.getCause() != root) {
      root = root.getCause();
    }
    return error.getClass().getSimpleName() + " / " + root.getClass().getSimpleName();
  }
}

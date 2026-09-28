package si.konferenca.registration.api;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import si.konferenca.registration.api.dto.ErrorResponse;
import si.konferenca.registration.api.dto.ValidationCodes;
import si.konferenca.registration.service.CaptchaVerificationException;
import si.konferenca.registration.service.FieldViolation;
import si.konferenca.registration.service.RegistrationValidationException;

/** Maps exceptions to the uniform error format without leaking internal details. */
@RestControllerAdvice
public class ApiExceptionHandler {

  static final String VALIDATION_FAILED = "VALIDATION_FAILED";

  private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

  private static final Map<String, String> CODE_MESSAGES =
      Map.of(
          ValidationCodes.REQUIRED,
          "This field is required.",
          ValidationCodes.TOO_LONG,
          "This value is too long.",
          ValidationCodes.INVALID_EMAIL,
          "Email must be a valid email address.",
          ValidationCodes.INVALID_CHARACTERS,
          "This value contains invalid characters.",
          ValidationCodes.TOO_MANY_OPTIONS,
          "Too many options selected.",
          "UNKNOWN_OPTION",
          "A selected option does not exist.",
          "INACTIVE_OPTION",
          "A selected option is no longer available.",
          "CONSENT_REQUIRED",
          "This consent is required.");

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleInvalidArgument(MethodArgumentNotValidException e) {
    Map<String, String> codeByField = new LinkedHashMap<>();
    e.getBindingResult()
        .getFieldErrors()
        .forEach(
            error -> {
              String code = error.getDefaultMessage();
              codeByField.merge(
                  error.getField(),
                  code,
                  (existing, candidate) ->
                      ValidationCodes.REQUIRED.equals(candidate) ? candidate : existing);
            });
    List<FieldViolation> violations = new ArrayList<>();
    codeByField.forEach((field, code) -> violations.add(new FieldViolation(field, code)));
    return validationFailed(violations);
  }

  @ExceptionHandler(RegistrationValidationException.class)
  public ResponseEntity<ErrorResponse> handleRegistrationValidation(
      RegistrationValidationException e) {
    return validationFailed(e.getViolations());
  }

  @ExceptionHandler(CaptchaVerificationException.class)
  public ResponseEntity<ErrorResponse> handleCaptcha(CaptchaVerificationException e) {
    return error(
        HttpStatus.BAD_REQUEST,
        "CAPTCHA_FAILED",
        "The anti-automation check failed. Please try again.");
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ErrorResponse> handleNotReadable(HttpMessageNotReadableException e) {
    return error(
        HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "The request body could not be processed.");
  }

  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  public ResponseEntity<ErrorResponse> handleMediaType(HttpMediaTypeNotSupportedException e) {
    return error(
        HttpStatus.UNSUPPORTED_MEDIA_TYPE,
        "UNSUPPORTED_MEDIA_TYPE",
        "The content type is not supported.");
  }

  @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
  public ResponseEntity<Void> handleNotAcceptable(HttpMediaTypeNotAcceptableException e) {
    return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).build();
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<ErrorResponse> handleMethod(HttpRequestMethodNotSupportedException e) {
    return error(
        HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "The request method is not allowed.");
  }

  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ErrorResponse> handleNotFound(NoResourceFoundException e) {
    return error(HttpStatus.NOT_FOUND, "NOT_FOUND", "The requested resource does not exist.");
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
    // Exception messages may contain submitted personal data (e.g. database constraint errors
    // echo the failing row), so only types and code locations are logged by default.
    LOG.error("Unexpected error while processing request: {}", describeWithoutMessages(e));
    LOG.debug("Unexpected error details", e);
    return error(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "INTERNAL_ERROR",
        "The request could not be completed. Please try again later.");
  }

  /** Exception class chain with the throwing code location of each cause, without messages. */
  static String describeWithoutMessages(Throwable throwable) {
    StringBuilder text = new StringBuilder();
    Throwable current = throwable;
    int depth = 0;
    while (current != null && depth < 10) {
      if (depth > 0) {
        text.append(" <- caused by ");
      }
      text.append(current.getClass().getName());
      StackTraceElement[] trace = current.getStackTrace();
      if (trace.length > 0) {
        text.append(" at ").append(trace[0]);
      }
      current = current.getCause() == current ? null : current.getCause();
      depth++;
    }
    return text.toString();
  }

  private static ResponseEntity<ErrorResponse> validationFailed(List<FieldViolation> violations) {
    List<ErrorResponse.FieldError> fieldErrors =
        violations.stream()
            .map(
                v ->
                    new ErrorResponse.FieldError(
                        v.field(),
                        v.code(),
                        CODE_MESSAGES.getOrDefault(v.code(), "Invalid value.")))
            .toList();
    return ResponseEntity.badRequest()
        .body(
            new ErrorResponse(
                VALIDATION_FAILED, "The registration contains invalid data.", fieldErrors));
  }

  private static ResponseEntity<ErrorResponse> error(
      HttpStatus status, String code, String message) {
    return ResponseEntity.status(status).body(new ErrorResponse(code, message));
  }
}

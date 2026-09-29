package si.konferenca.registration.web;

import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import si.konferenca.registration.service.FieldError;
import si.konferenca.registration.service.RegistrationExceptions.RecaptchaFailedException;
import si.konferenca.registration.service.RegistrationExceptions.RegistrationNotSavedException;
import si.konferenca.registration.service.RegistrationExceptions.ValidationFailedException;

/**
 * Maps every failure to RFC 9457 Problem Details with a contract {@code code} (specification §5.1,
 * §8.5). Never exposes exception messages, class names or stack traces.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

  private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

  /** A request body that is well-formed JSON but not the shape an endpoint accepts. */
  static class MalformedRequestException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    MalformedRequestException(String message) {
      super(message);
    }
  }

  @ExceptionHandler(ValidationFailedException.class)
  ResponseEntity<ProblemDetail> validationFailed(ValidationFailedException e) {
    ProblemDetail problem =
        problem(
            HttpStatus.BAD_REQUEST,
            "Validation failed",
            "VALIDATION_FAILED",
            "The registration contains invalid fields.");
    problem.setProperty(
        "errors", e.errors().stream().map(ApiExceptionHandler::fieldError).toList());
    return respond(problem);
  }

  @ExceptionHandler(RecaptchaFailedException.class)
  ResponseEntity<ProblemDetail> recaptchaFailed() {
    ProblemDetail problem =
        problem(
            HttpStatus.BAD_REQUEST,
            "reCAPTCHA verification failed",
            "RECAPTCHA_FAILED",
            FieldError.Code.RECAPTCHA_FAILED.message());
    problem.setProperty(
        "errors",
        List.of(fieldError(new FieldError("recaptchaToken", FieldError.Code.RECAPTCHA_FAILED))));
    return respond(problem);
  }

  @ExceptionHandler({HttpMessageNotReadableException.class, MalformedRequestException.class})
  ResponseEntity<ProblemDetail> malformed() {
    return respond(
        problem(
            HttpStatus.BAD_REQUEST,
            "Malformed request",
            "MALFORMED_REQUEST",
            "The request body is not valid for this endpoint."));
  }

  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  ResponseEntity<ProblemDetail> unsupportedMediaType() {
    return respond(
        problem(
            HttpStatus.UNSUPPORTED_MEDIA_TYPE,
            "Unsupported media type",
            "UNSUPPORTED_MEDIA_TYPE",
            "Send the request body as application/json."));
  }

  @ExceptionHandler({NoResourceFoundException.class, HttpRequestMethodNotSupportedException.class})
  ResponseEntity<ProblemDetail> notFound() {
    return respond(problem(HttpStatus.NOT_FOUND, "Not found", "NOT_FOUND", "No such resource."));
  }

  @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
  ResponseEntity<ProblemDetail> notAcceptable() {
    return respond(
        problem(
            HttpStatus.NOT_ACCEPTABLE,
            "Not acceptable",
            "MALFORMED_REQUEST",
            "Unsupported Accept header."));
  }

  @ExceptionHandler(RegistrationNotSavedException.class)
  ResponseEntity<ProblemDetail> notSaved() {
    return respond(
        problem(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Registration not saved",
            "REGISTRATION_NOT_SAVED",
            "The registration could not be completed. Please try again later."));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ProblemDetail> unexpected(Exception e) {
    LOG.error("Unexpected error while handling a request", e);
    return respond(
        problem(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Internal error",
            "INTERNAL_ERROR",
            "An unexpected error occurred."));
  }

  private static Map<String, String> fieldError(FieldError error) {
    return Map.of(
        "field", error.field(), "code", error.code().name(), "message", error.code().message());
  }

  private static ProblemDetail problem(
      HttpStatus status, String title, String code, String detail) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setTitle(title);
    problem.setProperty("code", code);
    return problem;
  }

  private static ResponseEntity<ProblemDetail> respond(ProblemDetail problem) {
    return ResponseEntity.status(problem.getStatus())
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(problem);
  }
}

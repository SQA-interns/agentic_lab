package org.conference.registration.api;

import java.util.LinkedHashMap;
import java.util.Map;
import org.conference.registration.api.dto.ApiResponses.ErrorResponse;
import org.conference.registration.service.RegistrationValidationException;
import org.conference.registration.service.SubmissionRejectedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Maps exceptions to the uniform error body; never exposes internals (AC-PC-07). */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);
  private static final String VALIDATION_FAILED = "VALIDATION_FAILED";
  private static final String MALFORMED_REQUEST = "MALFORMED_REQUEST";

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> invalidArgument(MethodArgumentNotValidException e) {
    Map<String, String> fieldErrors = new LinkedHashMap<>();
    for (FieldError error : e.getBindingResult().getFieldErrors()) {
      fieldErrors.putIfAbsent(topLevelField(error.getField()), error.getDefaultMessage());
    }
    return respond(
        HttpStatus.BAD_REQUEST,
        new ErrorResponse(
            400, VALIDATION_FAILED, "Please correct the highlighted fields.", fieldErrors));
  }

  @ExceptionHandler(RegistrationValidationException.class)
  public ResponseEntity<ErrorResponse> invalidRegistration(RegistrationValidationException e) {
    return respond(
        HttpStatus.BAD_REQUEST,
        new ErrorResponse(
            400,
            VALIDATION_FAILED,
            "Please correct the highlighted fields.",
            Map.of(e.getField(), e.getMessage())));
  }

  @ExceptionHandler(SubmissionRejectedException.class)
  public ResponseEntity<ErrorResponse> rejected(SubmissionRejectedException e) {
    LOG.warn("Submission rejected by anti-automation check: {}", e.getMessage());
    return respond(
        HttpStatus.BAD_REQUEST,
        new ErrorResponse(
            400,
            "SUBMISSION_REJECTED",
            "Your submission could not be accepted. Please reload the form and try again."));
  }

  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    MissingServletRequestParameterException.class,
    MethodArgumentTypeMismatchException.class
  })
  public ResponseEntity<ErrorResponse> malformed(Exception e) {
    return respond(
        HttpStatus.BAD_REQUEST,
        new ErrorResponse(400, MALFORMED_REQUEST, "The request could not be processed."));
  }

  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  public ResponseEntity<ErrorResponse> unsupportedMediaType(Exception e) {
    return respond(
        HttpStatus.UNSUPPORTED_MEDIA_TYPE,
        new ErrorResponse(415, "UNSUPPORTED_MEDIA_TYPE", "Content type must be application/json."));
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<ErrorResponse> methodNotAllowed(Exception e) {
    return respond(
        HttpStatus.METHOD_NOT_ALLOWED,
        new ErrorResponse(405, "METHOD_NOT_ALLOWED", "Method not allowed."));
  }

  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ErrorResponse> notFound(Exception e) {
    return respond(HttpStatus.NOT_FOUND, new ErrorResponse(404, "NOT_FOUND", "Not found."));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> unexpected(Exception e) {
    LOG.error("Unhandled error", e);
    return respond(
        HttpStatus.INTERNAL_SERVER_ERROR,
        new ErrorResponse(
            500, "INTERNAL_ERROR", "An unexpected error occurred. Please try again later."));
  }

  private static ResponseEntity<ErrorResponse> respond(HttpStatus status, ErrorResponse body) {
    return ResponseEntity.status(status).body(body);
  }

  /** {@code optionIds[2]} → {@code optionIds}. */
  private static String topLevelField(String path) {
    int bracket = path.indexOf('[');
    return bracket < 0 ? path : path.substring(0, bracket);
  }
}

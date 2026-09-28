package org.example.conference.shared.api;

import java.util.List;
import org.springframework.http.HttpStatus;

/** Business/API error mapped to an RFC 7807 response by {@link ApiExceptionHandler}. */
public class ApiException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final HttpStatus status;
  private final ErrorCode code;
  private final transient List<FieldError> errors;

  public ApiException(HttpStatus status, ErrorCode code, List<FieldError> errors) {
    super(code.name());
    this.status = status;
    this.code = code;
    this.errors = List.copyOf(errors);
  }

  public ApiException(HttpStatus status, ErrorCode code) {
    this(status, code, List.of());
  }

  /** Keeps the technical cause for diagnostics; it is never serialized to clients. */
  public ApiException(HttpStatus status, ErrorCode code, Throwable cause) {
    super(code.name(), cause);
    this.status = status;
    this.code = code;
    this.errors = List.of();
  }

  public HttpStatus getStatus() {
    return status;
  }

  public ErrorCode getCode() {
    return code;
  }

  public List<FieldError> getErrors() {
    return errors;
  }
}

package si.konferenca.registration.application;

import java.io.Serializable;
import java.util.List;

/** One or more fields of a registration are invalid (SB-01). */
public final class ValidationException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  /** One invalid field with its error code (openapi.yaml, FieldError). */
  public record FieldError(String field, String code, String message) implements Serializable {}

  private final FieldError[] errors;

  public ValidationException(List<FieldError> errors) {
    super("Invalid registration: " + errors.size() + " field errors");
    this.errors = errors.toArray(FieldError[]::new);
  }

  public List<FieldError> errors() {
    return List.of(errors);
  }
}

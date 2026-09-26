package si.konferenca.registration.application;

import java.util.List;

/** Thrown when submitted registration data violates the validation rules. */
public class ValidationException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final transient List<FieldViolation> violations;

  public ValidationException(List<FieldViolation> violations) {
    super("Registration input is invalid");
    this.violations = List.copyOf(violations);
  }

  public List<FieldViolation> getViolations() {
    return violations;
  }
}

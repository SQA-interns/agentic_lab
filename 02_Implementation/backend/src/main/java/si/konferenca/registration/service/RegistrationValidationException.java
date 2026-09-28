package si.konferenca.registration.service;

import java.util.List;

/** Business validation of a registration failed; nothing was stored. */
public class RegistrationValidationException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final transient List<FieldViolation> violations;

  public RegistrationValidationException(List<FieldViolation> violations) {
    super("Registration validation failed");
    this.violations = List.copyOf(violations);
  }

  public List<FieldViolation> getViolations() {
    return violations;
  }
}

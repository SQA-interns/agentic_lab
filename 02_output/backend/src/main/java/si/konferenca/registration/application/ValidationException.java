package si.konferenca.registration.application;

import java.util.List;

/** The submission was rejected; nothing was stored. Lists every offending field. */
public class ValidationException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /** One offending request field with a message for the participant. */
  public record FieldViolation(String field, String message) {}

  private final transient List<FieldViolation> violations;

  public ValidationException(List<FieldViolation> violations) {
    super("Registration rejected: " + violations.size() + " field(s)");
    this.violations = List.copyOf(violations);
  }

  public List<FieldViolation> violations() {
    return violations;
  }
}

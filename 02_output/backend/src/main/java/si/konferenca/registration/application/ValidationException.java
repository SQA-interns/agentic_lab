package si.konferenca.registration.application;

import java.util.List;

/** The registration was rejected; nothing was stored. */
public class ValidationException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final transient List<FieldError> errors;

  public ValidationException(List<FieldError> errors) {
    super("Registration rejected: " + errors.size() + " field error(s)");
    this.errors = List.copyOf(errors);
  }

  public List<FieldError> errors() {
    return errors;
  }
}

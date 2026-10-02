package si.konferenca.registration.domain;

import java.util.List;

/** The registration is rejected; nothing was stored or sent. */
public class ValidationFailedException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final transient List<FieldError> errors;

  public ValidationFailedException(List<FieldError> errors) {
    super("registration rejected: " + errors.size() + " field error(s)");
    this.errors = List.copyOf(errors);
  }

  public List<FieldError> errors() {
    return errors;
  }
}

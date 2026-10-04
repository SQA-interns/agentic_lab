package si.konferenca.registration.application;

import java.io.Serial;
import java.util.List;

/** The registration has invalid fields or failed the anti-automation check; nothing stored. */
public class RegistrationRejectedException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  private final List<FieldError> errors;

  public RegistrationRejectedException(List<FieldError> errors) {
    super("registration rejected");
    this.errors = List.copyOf(errors);
  }

  public List<FieldError> errors() {
    return errors;
  }
}

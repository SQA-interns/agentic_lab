package si.konferenca.registration.application;

import java.io.Serial;
import java.util.List;
import si.konferenca.registration.domain.FieldError;

/** A submission failed validation; nothing was stored. */
public class RegistrationRejectedException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  private final List<FieldError> errors;

  public RegistrationRejectedException(List<FieldError> errors) {
    super("registration rejected: " + errors.size() + " field error(s)");
    this.errors = List.copyOf(errors);
  }

  public List<FieldError> errors() {
    return errors;
  }
}

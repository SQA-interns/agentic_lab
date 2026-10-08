package si.konferenca.registration.service;

import java.util.List;
import si.konferenca.registration.domain.FieldError;

/** The registration is invalid; nothing was stored (400). */
public class RegistrationRejectedException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final transient List<FieldError> errors;

  public RegistrationRejectedException(List<FieldError> errors) {
    super("registration rejected", null, false, false);
    this.errors = List.copyOf(errors);
  }

  public List<FieldError> errors() {
    return errors;
  }
}

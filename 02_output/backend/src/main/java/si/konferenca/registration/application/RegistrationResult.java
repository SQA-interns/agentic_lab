package si.konferenca.registration.application;

import java.util.List;
import si.konferenca.registration.domain.Registration;
import si.konferenca.registration.domain.ValidationError;

/** Outcome of a registration attempt. */
public sealed interface RegistrationResult {

  /** Stored in the database and as a JSON copy. */
  record Accepted(Registration registration) implements RegistrationResult {}

  /** Rejected; nothing stored. {@code duplicate} distinguishes 409 from 400. */
  record Rejected(List<ValidationError> errors, boolean duplicate) implements RegistrationResult {
    public Rejected {
      errors = List.copyOf(errors);
    }
  }
}

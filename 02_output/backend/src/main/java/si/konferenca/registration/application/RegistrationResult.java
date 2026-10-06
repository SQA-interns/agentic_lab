package si.konferenca.registration.application;

import java.util.List;
import si.konferenca.registration.domain.FieldError;
import si.konferenca.registration.domain.Registration;

/** Outcome of a submitted registration. A storage failure is thrown, not returned. */
public sealed interface RegistrationResult {

  /** Stored in the database and as a JSON copy (BR-07). */
  record Accepted(Registration registration) implements RegistrationResult {}

  /** Rejected by validation (BR-01 to BR-05). */
  record Invalid(List<FieldError> errors) implements RegistrationResult {
    public Invalid {
      errors = List.copyOf(errors);
    }
  }

  /** The anti-automation token was not confirmed (SR-01). */
  record CaptchaRejected() implements RegistrationResult {}

  /** The verification service could not be reached; nothing was stored. */
  record CaptchaUnavailable() implements RegistrationResult {}

  /** The email is already registered (D-09). */
  record DuplicateEmail() implements RegistrationResult {}
}

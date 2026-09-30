package si.konferenca.registration.domain;

import java.util.List;

/** Outcome of validating a submission: either field errors or the accepted parts. */
public record ValidationResult(
    List<FieldError> errors,
    ParticipantDetails participant,
    List<ConferenceOption> options,
    List<ConsentDefinition> consents) {

  public ValidationResult {
    errors = List.copyOf(errors);
    options = List.copyOf(options);
    consents = List.copyOf(consents);
  }

  public boolean valid() {
    return errors.isEmpty();
  }
}

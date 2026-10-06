package si.konferenca.registration.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** An accepted registration with normalised values (BR-07). Student or external fields are null. */
public record Registration(
    UUID id,
    RegistrationType type,
    String firstName,
    String lastName,
    String email,
    String organization,
    String studyInstitution,
    String studyProgramme,
    String studentId,
    List<ConferenceOption> options,
    Consent consent,
    Instant receivedAt) {

  public Registration {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(receivedAt, "receivedAt");
    options = List.copyOf(options);
  }

  public String normalizedEmail() {
    return Text.normalizeEmail(email);
  }

  /** Options of one category in the order they were selected. */
  public List<ConferenceOption> optionsIn(OptionCategory category) {
    return options.stream().filter(o -> o.category() == category).toList();
  }
}

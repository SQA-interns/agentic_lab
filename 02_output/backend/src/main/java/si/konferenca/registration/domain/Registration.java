package si.konferenca.registration.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** An accepted registration, with option names and consent wording as shown at acceptance. */
public record Registration(
    UUID id,
    RegistrationType type,
    Participant participant,
    List<SelectedOption> options,
    List<GivenConsent> consents,
    Instant receivedAt) {

  /** A selected option, copied from the catalog at acceptance. */
  public record SelectedOption(String id, String name, OptionCategory category) {}

  /** A consent given, with its wording and time (SB-14). */
  public record GivenConsent(String id, String text, Instant givenAt) {}

  public Registration {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(participant, "participant");
    Objects.requireNonNull(receivedAt, "receivedAt");
    options = List.copyOf(options);
    consents = List.copyOf(consents);
  }

  /** Names of the selected options of one category, in selection order. */
  public List<String> optionNames(OptionCategory category) {
    return options.stream()
        .filter(o -> o.category() == category)
        .map(SelectedOption::name)
        .toList();
  }
}

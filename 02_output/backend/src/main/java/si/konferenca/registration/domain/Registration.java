package si.konferenca.registration.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** An accepted registration, exactly as it is stored, copied, mailed and exported. */
public record Registration(
    UUID id,
    RegistrationType type,
    Instant acceptedAt,
    Map<TextField, String> values,
    List<SelectedOption> options,
    Consent consent) {

  public Registration {
    values = Map.copyOf(values);
    options = List.copyOf(options);
  }

  /** An option as it was when the registration was accepted. */
  public record SelectedOption(String id, String name, OptionCategory category) {}

  /** The consent given with the registration (SB-14). */
  public record Consent(String id, String text, Instant givenAt) {}

  /** The value of a field, or null when the registration type does not have it. */
  public String value(TextField field) {
    return values.get(field);
  }
}

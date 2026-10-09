package si.konferenca.registration.domain;

import java.time.Instant;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** An accepted registration: values trimmed, options and consents resolved (BR-07). */
public record Registration(
    UUID id,
    RegistrationType type,
    Map<Field, String> values,
    List<ConferenceOption> selectedOptions,
    List<GivenConsent> consents,
    Instant submittedAt) {

  public Registration {
    EnumMap<Field, String> copy = new EnumMap<>(Field.class);
    copy.putAll(values);
    values = Collections.unmodifiableMap(copy);
    selectedOptions = List.copyOf(selectedOptions);
    consents = List.copyOf(consents);
  }

  public String value(Field field) {
    return values.get(field);
  }

  public String email() {
    return values.get(Field.EMAIL);
  }

  /** D-18: the email as compared for duplicates. */
  public String normalizedEmail() {
    return normalizeEmail(email());
  }

  public static String normalizeEmail(String email) {
    return Text.trim(email).toLowerCase(Locale.ROOT);
  }

  /** Names of the selected options of one category, in selection order. */
  public List<String> optionNames(Category category) {
    return selectedOptions.stream()
        .filter(o -> o.category() == category)
        .map(ConferenceOption::name)
        .toList();
  }
}

package si.konferenca.registration.domain;

import java.util.Locale;
import java.util.Optional;

/** The two fixed registration types (BR-01). */
public enum RegistrationType {
  EXTERNAL,
  STUDENT;

  /** Parses a type name; empty when the value is not a known type. */
  public static Optional<RegistrationType> parse(String value) {
    if (value == null) {
      return Optional.empty();
    }
    try {
      return Optional.of(valueOf(value.strip().toUpperCase(Locale.ROOT)));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }
}

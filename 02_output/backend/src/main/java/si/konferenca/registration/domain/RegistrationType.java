package si.konferenca.registration.domain;

import java.util.Optional;

/** The two registration types (BR-01). */
public enum RegistrationType {
  EXTERNAL("external", "External participant"),
  STUDENT("student", "Student");

  private final String value;
  private final String label;

  RegistrationType(String value, String label) {
    this.value = value;
    this.label = label;
  }

  /** The value used in the API, the database and the JSON copy. */
  public String value() {
    return value;
  }

  /** The name shown to people. */
  public String label() {
    return label;
  }

  public static Optional<RegistrationType> fromValue(String value) {
    for (RegistrationType type : values()) {
      if (type.value.equals(value)) {
        return Optional.of(type);
      }
    }
    return Optional.empty();
  }
}

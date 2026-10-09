package si.konferenca.registration.domain;

import java.util.Locale;
import java.util.Optional;

/** The two registration types (BR-01). */
public enum RegistrationType {
  EXTERNAL("External participant", "external participant"),
  STUDENT("Student", "student");

  private final String label;
  private final String lowerLabel;

  RegistrationType(String label, String lowerLabel) {
    this.label = label;
    this.lowerLabel = lowerLabel;
  }

  /** Label used in the export (for example "External participant"). */
  public String label() {
    return label;
  }

  /** Label used inside sentences (for example "external participant"). */
  public String lowerLabel() {
    return lowerLabel;
  }

  /** Parses the path form ("external", "student"). */
  public static Optional<RegistrationType> fromPath(String value) {
    for (RegistrationType type : values()) {
      if (type.name().toLowerCase(Locale.ROOT).equals(value)) {
        return Optional.of(type);
      }
    }
    return Optional.empty();
  }

  /** Parses the API form ("EXTERNAL", "STUDENT"). */
  public static Optional<RegistrationType> fromName(String value) {
    for (RegistrationType type : values()) {
      if (type.name().equals(value)) {
        return Optional.of(type);
      }
    }
    return Optional.empty();
  }
}

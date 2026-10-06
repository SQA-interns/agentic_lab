package si.konferenca.registration.domain;

import java.util.Optional;

/** Option categories (BR-04), in display order. */
public enum OptionCategory {
  WORKSHOP("workshop", "Workshops"),
  EVENT("event", "Events"),
  MEAL("meal", "Meals"),
  OTHER("other", "Other activities");

  private final String value;
  private final String label;

  OptionCategory(String value, String label) {
    this.value = value;
    this.label = label;
  }

  public String value() {
    return value;
  }

  /** Group heading in forms, emails and the export. */
  public String label() {
    return label;
  }

  public static Optional<OptionCategory> fromValue(String value) {
    for (OptionCategory category : values()) {
      if (category.value.equals(value)) {
        return Optional.of(category);
      }
    }
    return Optional.empty();
  }
}

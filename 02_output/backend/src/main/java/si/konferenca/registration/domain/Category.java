package si.konferenca.registration.domain;

import java.util.Arrays;
import java.util.Optional;

/** Option categories (BR-04), in the order they are shown. */
public enum Category {
  WORKSHOP("workshop", "Workshops"),
  EVENT("event", "Events"),
  MEAL("meal", "Meals"),
  OTHER("other", "Other activities");

  private final String value;
  private final String label;

  Category(String value, String label) {
    this.value = value;
    this.label = label;
  }

  /** The identifier used in configuration, the API, the database and the JSON copy. */
  public String value() {
    return value;
  }

  /** Plural heading used in emails and the export. */
  public String label() {
    return label;
  }

  public static Optional<Category> fromValue(String value) {
    return Arrays.stream(values()).filter(c -> c.value.equals(value)).findFirst();
  }
}

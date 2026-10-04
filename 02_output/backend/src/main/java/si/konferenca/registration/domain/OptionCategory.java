package si.konferenca.registration.domain;

import java.util.Arrays;
import java.util.Optional;

/** Option groups of BR-04; the value is the identifier used in contracts and storage. */
public enum OptionCategory {
  WORKSHOP("workshop"),
  EVENT("event"),
  MEAL("meal"),
  OTHER("other");

  private final String value;

  OptionCategory(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  public static Optional<OptionCategory> fromValue(String value) {
    return Arrays.stream(values()).filter(c -> c.value.equals(value)).findFirst();
  }
}

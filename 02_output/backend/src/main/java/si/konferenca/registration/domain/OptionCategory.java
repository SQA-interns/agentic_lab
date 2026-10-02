package si.konferenca.registration.domain;

import java.util.Arrays;
import java.util.Optional;

/** The category of a conference option (BR-04). */
public enum OptionCategory {
  WORKSHOP("workshop"),
  EVENT("event"),
  MEAL("meal"),
  OTHER("other");

  private final String code;

  OptionCategory(String code) {
    this.code = code;
  }

  /** The value used in configuration, API, database and JSON copy. */
  public String code() {
    return code;
  }

  public static Optional<OptionCategory> fromCode(String code) {
    return Arrays.stream(values()).filter(category -> category.code.equals(code)).findFirst();
  }
}

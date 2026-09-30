package si.konferenca.registration.domain;

import java.util.Arrays;
import java.util.Optional;

/** The four option groups of BR-04, with their external (JSON, database) names. */
public enum OptionCategory {
  WORKSHOP("workshop"),
  EVENT("event"),
  MEAL("meal"),
  OTHER("other");

  private final String code;

  OptionCategory(String code) {
    this.code = code;
  }

  public String code() {
    return code;
  }

  public static Optional<OptionCategory> fromCode(String code) {
    return Arrays.stream(values()).filter(c -> c.code.equals(code)).findFirst();
  }
}

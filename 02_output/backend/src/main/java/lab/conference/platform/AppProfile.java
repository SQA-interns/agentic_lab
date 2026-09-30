package lab.conference.platform;

import java.util.Locale;

/** Explicit deployment profile (APP_PROFILE); there is no implicit default. */
public enum AppProfile {
  LOCAL,
  TEST,
  PRODUCTION;

  /** Parses APP_PROFILE, failing for missing or unknown values. */
  public static AppProfile parse(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalStateException("APP_PROFILE must be set to local, test or production");
    }
    try {
      return valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new IllegalStateException("APP_PROFILE must be local, test or production", e);
    }
  }

  public boolean allowsTestSubstitutes() {
    return this != PRODUCTION;
  }
}

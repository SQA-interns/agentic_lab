package si.konferenca.registration.api.dto;

/** Validation codes used as Bean Validation messages and returned as field error codes. */
public final class ValidationCodes {

  public static final String REQUIRED = "REQUIRED";
  public static final String TOO_LONG = "TOO_LONG";
  public static final String INVALID_EMAIL = "INVALID_EMAIL";
  public static final String INVALID_CHARACTERS = "INVALID_CHARACTERS";
  public static final String TOO_MANY_OPTIONS = "TOO_MANY_OPTIONS";

  /** Simple email syntax check in addition to {@code @Email}: local@domain.tld. */
  public static final String EMAIL_PATTERN = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$";

  /** Any Unicode text without control characters. */
  public static final String NO_CONTROL_CHARS = "^[^\\p{Cntrl}]*$";

  public static final int MAX_NAME = 100;
  public static final int MAX_EMAIL = 254;
  public static final int MAX_TEXT = 200;
  public static final int MAX_STUDENT_ID = 50;
  public static final int MAX_OPTIONS = 50;
  public static final int MAX_TOKEN = 4000;

  private ValidationCodes() {}

  /** Trims leading and trailing (Unicode) whitespace; keeps {@code null}. */
  public static String trim(String value) {
    return value == null ? null : value.strip();
  }
}

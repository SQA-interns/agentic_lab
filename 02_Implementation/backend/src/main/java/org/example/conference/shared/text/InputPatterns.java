package org.example.conference.shared.text;

/** Shared validation patterns for participant input. */
public final class InputPatterns {

  /** No control characters (prevents header/log injection via CR/LF etc.). */
  public static final String NO_CONTROL_CHARS = "^[^\\p{Cc}]*$";

  /**
   * Pragmatic ASCII email syntax: local part, '@', at least two DNS labels. Internationalized
   * addresses are not accepted (documented choice).
   */
  public static final String EMAIL =
      "^[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+(\\.[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+)*"
          + "@[A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?"
          + "(\\.[A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+$";

  public static final int MAX_NAME = 100;
  public static final int MAX_LONG_TEXT = 200;
  public static final int MAX_STUDENT_ID = 64;
  public static final int MAX_EMAIL = 254;

  private InputPatterns() {}
}

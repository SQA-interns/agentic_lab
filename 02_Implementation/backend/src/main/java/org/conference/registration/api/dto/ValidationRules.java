package org.conference.registration.api.dto;

/** Shared field rules (specification §5). Mirrored in the frontend {@code validation/rules.ts}. */
public final class ValidationRules {

  /** Unicode letters/marks, then letters, spaces and . ' ’ - */
  public static final String NAME = "^[\\p{L}\\p{M}][\\p{L}\\p{M} .'’-]*$";

  public static final String EMAIL = "^[^@\\s\\p{Cc}]+@[^@\\s\\p{Cc}]+\\.[^@\\s\\p{Cc}]{2,}$";

  /** Free text: anything except control characters (CR/LF included). */
  public static final String TEXT = "^[^\\p{Cc}]*$";

  public static final String STUDENT_ID = "^[\\p{L}\\p{N}/-]+$";

  public static final String OPTION_ID = "^[a-z0-9-]{1,64}$";

  public static final int NAME_MAX = 100;
  public static final int EMAIL_MAX = 254;
  public static final int TEXT_MAX = 200;
  public static final int STUDENT_ID_MAX = 50;
  public static final int OPTIONS_MAX = 50;
  public static final int TOKEN_MAX = 512;
  public static final int HONEYPOT_MAX = 200;

  public static final String MSG_REQUIRED = "This field is required.";
  public static final String MSG_NAME = "Please use letters, spaces, apostrophes or hyphens only.";
  public static final String MSG_EMAIL = "Please enter a valid email address.";
  public static final String MSG_TEXT = "This field contains invalid characters.";
  public static final String MSG_STUDENT_ID = "Please use letters, digits, '/' or '-' only.";
  public static final String MSG_CONSENT = "You must accept the privacy statement.";
  public static final String MSG_OPTION = "One or more selected options are not available.";
  public static final String MSG_TOO_LONG = "This value is too long (maximum {max} characters).";

  private ValidationRules() {}
}

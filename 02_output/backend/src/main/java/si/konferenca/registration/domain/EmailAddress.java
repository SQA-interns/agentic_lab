package si.konferenca.registration.domain;

import java.util.Locale;
import java.util.regex.Pattern;

/** Email format of BR-03 (docs/02_specification.md §4); the frontend uses the same pattern. */
public final class EmailAddress {

  private static final Pattern FORMAT = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
  public static final int MAX_LENGTH = 254;

  private EmailAddress() {}

  public static boolean isValid(String value) {
    return value != null && value.length() <= MAX_LENGTH && FORMAT.matcher(value).matches();
  }

  /** The form used for the duplicate check (D-09): trimmed and lower case. */
  public static String normalize(String value) {
    return value.strip().toLowerCase(Locale.ROOT);
  }
}

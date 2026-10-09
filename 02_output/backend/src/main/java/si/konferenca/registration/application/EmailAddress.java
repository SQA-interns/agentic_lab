package si.konferenca.registration.application;

import java.util.regex.Pattern;

/** The email format rule shared by validation and configuration checks (BR-03). */
public final class EmailAddress {

  private static final Pattern FORMAT = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

  private EmailAddress() {}

  public static boolean isValid(String value) {
    return value != null && FORMAT.matcher(value).matches();
  }
}

package si.konferenca.registration.domain;

import java.util.regex.Pattern;

/** The email format rule of BR-03 (02_specification.md 4.1). */
public final class EmailAddresses {

  public static final int MAX_LENGTH = 254;

  private static final Pattern FORMAT = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

  private EmailAddresses() {}

  public static boolean isValid(String address) {
    return address != null && address.length() <= MAX_LENGTH && FORMAT.matcher(address).matches();
  }
}

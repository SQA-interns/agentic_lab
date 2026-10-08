package si.konferenca.registration.domain;

import java.util.Locale;
import java.util.regex.Pattern;

/** The email format rule (BR-03) and the normalised form used for duplicates (D-15). */
public final class EmailAddress {

  public static final int MAX_LENGTH = 254;

  private static final Pattern LOCAL = Pattern.compile("[^\\s\",;<>@]{1,64}");
  private static final Pattern LABEL =
      Pattern.compile("[\\p{L}\\p{N}](?:[\\p{L}\\p{N}-]*[\\p{L}\\p{N}])?");
  private static final Pattern TOP_LEVEL = Pattern.compile("\\p{L}{2,}");

  private EmailAddress() {}

  /** True for one local part, one {@code @}, and a domain of at least two labels. */
  public static boolean isValid(String email) {
    if (email == null || email.length() > MAX_LENGTH || Text.hasControlCharacters(email)) {
      return false;
    }
    int at = email.indexOf('@');
    if (at <= 0 || at != email.lastIndexOf('@')) {
      return false;
    }
    String local = email.substring(0, at);
    String domain = email.substring(at + 1);
    if (!LOCAL.matcher(local).matches()) {
      return false;
    }
    String[] labels = domain.split("\\.", -1);
    if (labels.length < 2) {
      return false;
    }
    for (String label : labels) {
      if (label.length() > 63 || !LABEL.matcher(label).matches()) {
        return false;
      }
    }
    return TOP_LEVEL.matcher(labels[labels.length - 1]).matches();
  }

  /** Lower-cased form for the one-registration-per-email rule. */
  public static String normalise(String email) {
    return email.toLowerCase(Locale.ROOT);
  }
}

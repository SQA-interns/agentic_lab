package si.konferenca.registration.domain;

import java.util.Locale;

/** Text rules shared by validation and storage (spec section 4, KP-03). */
public final class Text {

  private Text() {}

  /** True for every character the specification treats as whitespace, including U+00A0. */
  public static boolean isWhitespace(int c) {
    return Character.isWhitespace(c) || Character.isSpaceChar(c) || c == 0xFEFF || c == 0x85;
  }

  /** Removes leading and trailing whitespace; null stays null. */
  public static String strip(String value) {
    if (value == null) {
      return null;
    }
    int start = 0;
    int end = value.length();
    while (start < end && isWhitespace(value.codePointAt(start))) {
      start += Character.charCount(value.codePointAt(start));
    }
    while (end > start && isWhitespace(value.codePointBefore(end))) {
      end -= Character.charCount(value.codePointBefore(end));
    }
    return value.substring(start, end);
  }

  public static boolean isBlank(String value) {
    return value == null || strip(value).isEmpty();
  }

  /** True if the value contains a control or format character or a line/paragraph separator. */
  public static boolean hasForbiddenCharacters(String value) {
    return value
        .codePoints()
        .anyMatch(
            c -> {
              int t = Character.getType(c);
              return t == Character.CONTROL
                  || t == Character.FORMAT
                  || t == Character.LINE_SEPARATOR
                  || t == Character.PARAGRAPH_SEPARATOR;
            });
  }

  public static int length(String value) {
    return value.codePointCount(0, value.length());
  }

  /** The form used to detect a repeated email address (D-16). */
  public static String normalizeEmail(String email) {
    return strip(email).toLowerCase(Locale.ROOT);
  }
}

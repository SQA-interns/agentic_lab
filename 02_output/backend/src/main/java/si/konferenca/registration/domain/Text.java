package si.konferenca.registration.domain;

/**
 * Text rules shared by all fields (BR-02, BR-03, KP-03, SR-05).
 *
 * <p>Whitespace is the Unicode {@code White_Space} property plus U+FEFF, so the no-break space
 * U+00A0 is trimmed; {@link String#strip()} would keep it.
 */
public final class Text {

  private Text() {}

  /** Unicode White_Space code points, plus the byte order mark that browsers also trim. */
  public static boolean isWhitespace(int cp) {
    return (cp >= 0x09 && cp <= 0x0D)
        || cp == 0x20
        || cp == 0x85
        || cp == 0xA0
        || cp == 0x1680
        || (cp >= 0x2000 && cp <= 0x200A)
        || cp == 0x2028
        || cp == 0x2029
        || cp == 0x202F
        || cp == 0x205F
        || cp == 0x3000
        || cp == 0xFEFF;
  }

  /** Removes leading and trailing whitespace; null stays null. */
  public static String trim(String value) {
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

  /** True when the trimmed value is null or empty. */
  public static boolean isBlank(String value) {
    return value == null || trim(value).isEmpty();
  }

  /** True when the value contains a control or format character (for example CR, LF, NUL). */
  public static boolean hasControlCharacters(String value) {
    return value
        .codePoints()
        .anyMatch(
            cp -> {
              int type = Character.getType(cp);
              return type == Character.CONTROL || type == Character.FORMAT;
            });
  }

  /** Length in code points, so that letters outside the BMP count once. */
  public static int length(String value) {
    return value.codePointCount(0, value.length());
  }
}

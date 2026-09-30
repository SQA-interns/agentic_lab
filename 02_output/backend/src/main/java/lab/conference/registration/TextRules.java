package lab.conference.registration;

import java.util.regex.Pattern;

/** Unicode-aware trimming, control-character and email rules (BR-01, BR-02, SR-04). */
public final class TextRules {

  /**
   * Local part: RFC 5322 atext with single dots; domain: at least two DNS labels. Deliberately
   * ASCII-only; the length limits are checked separately.
   */
  private static final Pattern EMAIL =
      Pattern.compile(
          "^[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+(\\.[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+)*"
              + "@[A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?"
              + "(\\.[A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+$");

  private TextRules() {}

  static boolean isTrimmable(int codePoint) {
    return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint);
  }

  /** Removes leading and trailing whitespace including NBSP (U+00A0, U+2007, U+202F). */
  public static String trim(String value) {
    int start = 0;
    int end = value.length();
    while (start < end) {
      int cp = value.codePointAt(start);
      if (!isTrimmable(cp)) {
        break;
      }
      start += Character.charCount(cp);
    }
    while (end > start) {
      int cp = value.codePointBefore(end);
      if (!isTrimmable(cp)) {
        break;
      }
      end -= Character.charCount(cp);
    }
    return value.substring(start, end);
  }

  /** True if the value contains control characters or line/paragraph separators. */
  public static boolean hasControlCharacters(String value) {
    return value
        .codePoints()
        .anyMatch(
            cp ->
                Character.isISOControl(cp)
                    || cp == 0x2028
                    || cp == 0x2029
                    || Character.getType(cp) == Character.FORMAT && cp != 0x200C && cp != 0x200D);
  }

  public static int length(String value) {
    return value.codePointCount(0, value.length());
  }

  public static boolean isEmail(String value) {
    int at = value.lastIndexOf('@');
    return at > 0 && at <= 64 && EMAIL.matcher(value).matches();
  }
}

package si.konferenca.registration.domain;

/** Text normalisation shared by every field (BR-02, KP-03). */
public final class Text {

  private Text() {}

  /**
   * True for characters with the Unicode White_Space property plus U+FEFF, the set JavaScript's
   * {@code trim()} removes. {@link Character#isWhitespace} alone misses U+00A0, U+2007 and U+202F.
   */
  public static boolean isWhitespace(int codePoint) {
    return Character.isWhitespace(codePoint)
        || Character.isSpaceChar(codePoint)
        || codePoint == 0x0085
        || codePoint == 0xFEFF;
  }

  /** Removes leading and trailing whitespace as defined by {@link #isWhitespace(int)}. */
  public static String trim(String value) {
    if (value == null) {
      return "";
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

  /** True if the value contains a C0 control character or DEL. */
  public static boolean hasControlCharacter(String value) {
    return value.chars().anyMatch(c -> c < 0x20 || c == 0x7F);
  }
}

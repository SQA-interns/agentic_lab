package org.example.conference.shared.text;

/**
 * Removes insignificant leading/trailing Unicode whitespace (including no-break spaces) while
 * preserving interior text exactly. No Unicode normalization is applied.
 */
public final class TextNormalizer {

  private static final int ZERO_WIDTH_NO_BREAK_SPACE = 0xFEFF;
  private static final int ZERO_WIDTH_SPACE = 0x200B;

  private TextNormalizer() {}

  public static String trim(String value) {
    if (value == null) {
      return null;
    }
    int start = 0;
    int end = value.length();
    while (start < end && isInsignificant(value.codePointAt(start))) {
      start += Character.charCount(value.codePointAt(start));
    }
    while (end > start && isInsignificant(value.codePointBefore(end))) {
      end -= Character.charCount(value.codePointBefore(end));
    }
    return value.substring(start, end);
  }

  static boolean isInsignificant(int codePoint) {
    return Character.isWhitespace(codePoint)
        || Character.isSpaceChar(codePoint)
        || codePoint == ZERO_WIDTH_NO_BREAK_SPACE
        || codePoint == ZERO_WIDTH_SPACE;
  }
}

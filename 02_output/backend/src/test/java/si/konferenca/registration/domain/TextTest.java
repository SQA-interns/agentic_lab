package si.konferenca.registration.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TextTest {

  @ParameterizedTest(name = "KP-03 code point U+{0} is whitespace")
  @ValueSource(
      ints = {
        0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x20, 0x85, 0xA0, 0x1680, 0x2000, 0x2005, 0x200A, 0x2028,
        0x2029, 0x202F, 0x205F, 0x3000, 0xFEFF
      })
  void whitespaceCodePoints(int cp) {
    assertThat(Text.isWhitespace(cp)).isTrue();
  }

  @ParameterizedTest(name = "code point U+{0} is not whitespace")
  @ValueSource(ints = {0x08, 0x0E, 0x41, 0x0161, 0x200B, 0x1F600})
  void otherCodePoints(int cp) {
    assertThat(Text.isWhitespace(cp)).isFalse();
  }

  @Test
  @DisplayName("KP-03 trim removes NBSP, em space and BOM on both ends only")
  void trimsUnicodeWhitespaceAtTheEnds() {
    assertThat(Text.trim("  ﻿ Ana  Novak\t ")).isEqualTo("Ana  Novak");
  }

  @Test
  void trimKeepsNullAndEmpty() {
    assertThat(Text.trim(null)).isNull();
    assertThat(Text.trim("")).isEmpty();
    assertThat(Text.trim("  ")).isEmpty();
  }

  @Test
  void trimKeepsSupplementaryCharacters() {
    assertThat(Text.trim(" 😀x😀 ")).isEqualTo("😀x😀");
  }

  @Test
  void blankDetection() {
    assertThat(Text.isBlank(null)).isTrue();
    assertThat(Text.isBlank(" ")).isTrue();
    assertThat(Text.isBlank(" a ")).isFalse();
  }

  @Test
  @DisplayName("SR-05 control and format characters are detected")
  void controlCharacters() {
    assertThat(Text.hasControlCharacters("Ana\r\nBcc: x")).isTrue();
    assertThat(Text.hasControlCharacters("a\u0000b")).isTrue();
    assertThat(Text.hasControlCharacters("a‎b")).isTrue();
    assertThat(Text.hasControlCharacters("Čedomir Šuštaršič-Ćirić")).isFalse();
  }

  @Test
  void lengthCountsCodePoints() {
    assertThat(Text.length("😀ab")).isEqualTo(3);
    assertThat(Text.length("čšž")).isEqualTo(3);
  }
}

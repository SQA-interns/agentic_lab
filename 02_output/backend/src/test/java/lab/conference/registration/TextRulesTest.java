package lab.conference.registration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TextRulesTest {

  @Test
  void trimRemovesUnicodeSpacesIncludingNbspButKeepsInnerContent() {
    assertThat(TextRules.trim("   Žiga Čedomir  \t")).isEqualTo("Žiga Čedomir");
    assertThat(TextRules.trim("　x　")).isEqualTo("x");
    assertThat(TextRules.trim("   ")).isEmpty();
    assertThat(TextRules.trim("")).isEmpty();
    assertThat(TextRules.trim("abc")).isEqualTo("abc");
  }

  @Test
  void trimHandlesSupplementaryCharacters() {
    assertThat(TextRules.trim(" 😀 ")).isEqualTo("😀");
    assertThat(TextRules.length("😀Š")).isEqualTo(2);
  }

  @ParameterizedTest
  @ValueSource(strings = {"a\nb", "a\rb", "a\u0000b", "a b", "a b", "a‮b", "a\u0085b"})
  void controlAndBidiCharactersAreDetected(String value) {
    assertThat(TextRules.hasControlCharacters(value)).isTrue();
  }

  @ParameterizedTest
  @ValueSource(strings = {"Žiga Čedomir", "O'Brien-Šuštar", "a‍b", "a‌b", "<b>x</b> & y"})
  void ordinaryTextIsAllowed(String value) {
    assertThat(TextRules.hasControlCharacters(value)).isFalse();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "a@example.test",
        "first.last+tag@sub.example.co",
        "x_y@a-b.example",
        "o'reilly@example.test",
        "UPPER@EXAMPLE.TEST"
      })
  void validEmails(String email) {
    assertThat(TextRules.isEmail(email)).isTrue();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "",
        "plain",
        "@example.test",
        "a@",
        "a@localhost",
        "a@@example.test",
        "a b@example.test",
        ".a@example.test",
        "a.@example.test",
        "a..b@example.test",
        "a@-example.test",
        "a@example-.test",
        "a@example..test",
        "ž@example.test"
      })
  void invalidEmails(String email) {
    assertThat(TextRules.isEmail(email)).isFalse();
  }

  @Test
  void localPartLongerThan64IsRejected() {
    assertThat(TextRules.isEmail("a".repeat(64) + "@example.test")).isTrue();
    assertThat(TextRules.isEmail("a".repeat(65) + "@example.test")).isFalse();
  }
}

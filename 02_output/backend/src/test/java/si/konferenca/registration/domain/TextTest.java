package si.konferenca.registration.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TextTest {

  @ParameterizedTest
  @ValueSource(
      strings = {
        "\u00A0", "\u2007", "\u202F", "\u3000", "\uFEFF", "\u0085", "\t", "\n", "\u2028", " "
      })
  void stripRemovesEveryWhitespaceKindAtTheEdges(String ws) {
    assertThat(Text.strip(ws + "Ana" + ws)).isEqualTo("Ana");
    assertThat(Text.isWhitespace(ws.codePointAt(0))).isTrue();
  }

  @Test
  void stripKeepsInnerWhitespaceAndNull() {
    assertThat(Text.strip(" Ana\u00A0Marija ")).isEqualTo("Ana\u00A0Marija");
    assertThat(Text.strip(null)).isNull();
    assertThat(Text.strip("")).isEmpty();
  }

  @Test
  void stripHandlesSupplementaryCharacters() {
    assertThat(Text.strip("\u00A0😀\u00A0")).isEqualTo("😀");
  }

  @Test
  void isBlankTreatsNoBreakSpacesAsBlank() {
    assertThat(Text.isBlank(null)).isTrue();
    assertThat(Text.isBlank("\u00A0 \u202F")).isTrue();
    assertThat(Text.isBlank(" a ")).isFalse();
  }

  @Test
  void letterIsNotWhitespace() {
    assertThat(Text.isWhitespace('a')).isFalse();
    assertThat(Text.isWhitespace('č')).isFalse();
  }

  @ParameterizedTest
  @ValueSource(strings = {"a\u0000b", "a\rb", "a\u200Bb", "a\u2028b", "a\u2029b", "a\u001Fb"})
  void controlFormatAndSeparatorCharactersAreForbidden(String value) {
    assertThat(Text.hasForbiddenCharacters(value)).isTrue();
  }

  @Test
  void slovenianLettersAndPunctuationAreAllowed() {
    assertThat(Text.hasForbiddenCharacters("Čšž ĆĐ – \"<b>\" & 'x'")).isFalse();
  }

  @Test
  void lengthCountsCodePoints() {
    assertThat(Text.length("😀č")).isEqualTo(2);
  }

  @Test
  void normalizeEmailTrimsAndLowerCases() {
    assertThat(Text.normalizeEmail("\u00A0Ana.Novak@Example.SI "))
        .isEqualTo("ana.novak@example.si");
  }
}

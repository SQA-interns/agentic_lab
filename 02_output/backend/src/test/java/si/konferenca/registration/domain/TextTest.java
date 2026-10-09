package si.konferenca.registration.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TextTest {

  @ParameterizedTest
  @ValueSource(strings = {" ", "\t", "\n", "\r", " ", " ", " ", "　", "﻿", "\u0085"})
  void kp03_br02_unicodeWhitespaceIsTrimmed(String space) {
    assertThat(Text.trim(space + "Ana" + space)).isEqualTo("Ana");
    assertThat(Text.trim(space + space)).isEmpty();
  }

  @Test
  void br02_innerWhitespaceIsKept() {
    assertThat(Text.trim(" Ana Marija ")).isEqualTo("Ana Marija");
  }

  @Test
  void br03_lettersOutsideTheBasicPlaneAreKept() {
    String withSurrogates = " 𝒜na Čšž ";
    assertThat(Text.trim(withSurrogates)).isEqualTo("𝒜na Čšž");
  }

  @Test
  void nullIsTreatedAsEmpty() {
    assertThat(Text.trim(null)).isEmpty();
  }

  @Test
  void controlCharactersAreDetected() {
    assertThat(Text.hasControlCharacter("Ana\u0000")).isTrue();
    assertThat(Text.hasControlCharacter("a\nb")).isTrue();
    assertThat(Text.hasControlCharacter("a\u007Fb")).isTrue();
    assertThat(Text.hasControlCharacter("Čšž ana")).isFalse();
  }
}

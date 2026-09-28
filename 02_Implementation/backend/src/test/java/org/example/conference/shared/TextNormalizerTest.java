package org.example.conference.shared;

import static org.assertj.core.api.Assertions.assertThat;

import org.example.conference.shared.text.TextNormalizer;
import org.junit.jupiter.api.Test;

class TextNormalizerTest {

  @Test
  void trimsUnicodeWhitespaceIncludingNoBreakSpaces() {
    assertThat(TextNormalizer.trim(" Špela ")).isEqualTo("Špela");
    assertThat(TextNormalizer.trim("  ﻿​　 Ana \t\n")).isEqualTo("Ana");
    assertThat(TextNormalizer.trim("  ")).isEmpty();
    assertThat(TextNormalizer.trim(null)).isNull();
  }

  @Test
  void preservesInteriorTextExactly() {
    assertThat(TextNormalizer.trim(" Ana Marija  Čeh ")).isEqualTo("Ana Marija  Čeh");
    String decomposed = "Špela";
    assertThat(TextNormalizer.trim(decomposed)).isEqualTo(decomposed);
    assertThat(TextNormalizer.trim("😀x😀")).isEqualTo("😀x😀");
  }
}

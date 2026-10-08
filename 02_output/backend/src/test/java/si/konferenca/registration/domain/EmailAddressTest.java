package si.konferenca.registration.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EmailAddressTest {

  @ParameterizedTest(name = "BR-03 valid [{0}]")
  @ValueSource(
      strings = {
        "ana@example.si",
        "ana.novak+konf@sub.example.com",
        "a@b.co",
        "žan@šola.si",
        "o'brien@example.org",
        "x@xn--d1acufc.si"
      })
  void validAddresses(String email) {
    assertThat(EmailAddress.isValid(email)).isTrue();
  }

  @ParameterizedTest(name = "BR-03 invalid [{0}]")
  @ValueSource(
      strings = {
        "",
        "plain",
        "@example.si",
        "ana@",
        "ana@example",
        "ana@@example.si",
        "a@b@example.si",
        "ana novak@example.si",
        "ana@example..si",
        "ana@-example.si",
        "ana@example-.si",
        "ana@example.s",
        "ana@example.123",
        "ana<x>@example.si",
        "ana@example.si\r\nBcc: x@y.si",
        "\"ana\"@example.si"
      })
  void invalidAddresses(String email) {
    assertThat(EmailAddress.isValid(email)).isFalse();
  }

  @Test
  void nullIsInvalid() {
    assertThat(EmailAddress.isValid(null)).isFalse();
  }

  @Test
  void lengthLimits() {
    String local65 = "a".repeat(65);
    assertThat(EmailAddress.isValid(local65 + "@example.si")).isFalse();
    assertThat(EmailAddress.isValid("a".repeat(64) + "@example.si")).isTrue();
    String longDomain = "a".repeat(63) + "." + "b".repeat(63) + "." + "c".repeat(63) + ".si";
    assertThat(EmailAddress.isValid("a".repeat(60) + "@" + longDomain)).isFalse();
    assertThat(EmailAddress.isValid("x@" + "a".repeat(64) + ".si")).isFalse();
  }

  @Test
  void normaliseLowerCasesWithRootLocale() {
    assertThat(EmailAddress.normalise("Ana.NOVAK@Example.SI")).isEqualTo("ana.novak@example.si");
    assertThat(EmailAddress.normalise("TITLE@EXAMPLE.SI")).isEqualTo("title@example.si");
  }
}

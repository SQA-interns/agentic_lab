package si.konferenca.registration.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import si.konferenca.registration.domain.ErrorCode;
import si.konferenca.registration.domain.Field;
import si.konferenca.registration.domain.RegistrationType;
import si.konferenca.registration.domain.ValidationError;

class RegistrationRequestParserTest {

  private static final String VALID =
      "{\"type\":\"STUDENT\",\"firstName\":\"Luka\",\"lastName\":\"K\",\"email\":\"l@x.si\","
          + "\"studyInstitution\":\"UM\",\"studyProgramme\":\"I\",\"studentId\":\"E1\","
          + "\"optionIds\":[\"a\"],\"consentIds\":[\"p\"],\"recaptchaToken\":\"tok\"}";

  @Test
  void validBodyBecomesASubmission() {
    RegistrationRequestParser.Parsed parsed = RegistrationRequestParser.parse(VALID);

    assertThat(parsed.errors()).isEmpty();
    assertThat(parsed.submission().type()).isEqualTo(RegistrationType.STUDENT);
    assertThat(parsed.submission().values()).containsEntry(Field.STUDENT_ID, "E1");
    assertThat(parsed.submission().optionIds()).containsExactly("a");
    assertThat(parsed.submission().consentIds()).containsExactly("p");
    assertThat(parsed.captchaToken()).isEqualTo("tok");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "not json",
        "[]",
        "\"text\"",
        "{\"type\":\"VIP\"}",
        "{\"type\":7}",
        "{\"firstName\":\"Ana\"}",
        "{\"type\":\"EXTERNAL\",\"isAdmin\":true}"
      })
  void structuralProblemsAreMalformed(String body) {
    assertThat(RegistrationRequestParser.parse(body).errors())
        .containsExactly(ValidationError.of("type", ErrorCode.MALFORMED));
  }

  @Test
  void ac00104_missingFieldsOfTheTypeBecomeEmpty() {
    RegistrationRequestParser.Parsed parsed =
        RegistrationRequestParser.parse("{\"type\":\"EXTERNAL\"}");

    assertThat(parsed.submission().values())
        .containsEntry(Field.FIRST_NAME, "")
        .containsEntry(Field.ORGANIZATION, "")
        .doesNotContainKey(Field.STUDENT_ID);
    assertThat(parsed.submission().optionIds()).isEmpty();
    assertThat(parsed.captchaToken()).isNull();
  }

  @Test
  void nonStringFieldIsMalformedForThatField() {
    assertThat(
            RegistrationRequestParser.parse("{\"type\":\"EXTERNAL\",\"firstName\":{\"x\":1}}")
                .errors())
        .containsExactly(ValidationError.of("firstName", ErrorCode.MALFORMED));
  }

  @Test
  void badIdListsAreMalformed() {
    assertThat(
            RegistrationRequestParser.parse("{\"type\":\"EXTERNAL\",\"optionIds\":\"a\"}").errors())
        .containsExactly(ValidationError.of("optionIds", ErrorCode.MALFORMED));
    assertThat(
            RegistrationRequestParser.parse("{\"type\":\"EXTERNAL\",\"consentIds\":[1]}").errors())
        .containsExactly(ValidationError.of("consentIds", ErrorCode.MALFORMED));
    String tooMany = "[" + "\"a\",".repeat(50) + "\"a\"]";
    assertThat(
            RegistrationRequestParser.parse("{\"type\":\"EXTERNAL\",\"optionIds\":" + tooMany + "}")
                .errors())
        .containsExactly(ValidationError.of("optionIds", ErrorCode.MALFORMED));
  }

  @Test
  void listsAtTheirMaximumSizeAreAccepted() {
    String fifty = "[" + "\"a\",".repeat(49) + "\"a\"]";
    String twenty = "[" + "\"c\",".repeat(19) + "\"c\"]";

    RegistrationRequestParser.Parsed parsed =
        RegistrationRequestParser.parse(
            "{\"type\":\"EXTERNAL\",\"optionIds\":" + fifty + ",\"consentIds\":" + twenty + "}");

    assertThat(parsed.errors()).isEmpty();
    assertThat(parsed.submission().optionIds()).hasSize(50);
    assertThat(parsed.submission().consentIds()).hasSize(20);
  }

  @Test
  void tokenAtTheMaximumLengthIsKept() {
    String token = "t".repeat(RegistrationRequestParser.MAX_TOKEN);

    assertThat(
            RegistrationRequestParser.parse(
                    "{\"type\":\"EXTERNAL\",\"recaptchaToken\":\"" + token + "\"}")
                .captchaToken())
        .isEqualTo(token);
  }

  @Test
  void overlongTokenIsDropped() {
    String token = "t".repeat(RegistrationRequestParser.MAX_TOKEN + 1);

    assertThat(
            RegistrationRequestParser.parse(
                    "{\"type\":\"EXTERNAL\",\"recaptchaToken\":\"" + token + "\"}")
                .captchaToken())
        .isNull();
  }
}

package si.konferenca.registration.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import si.konferenca.registration.domain.RegistrationInput;
import si.konferenca.registration.domain.RegistrationType;
import si.konferenca.registration.domain.TextField;
import si.konferenca.registration.domain.ValidationFailedException;

/** The shape checks of the request body (SB-01): kinds of values and unknown properties. */
class RegistrationRequestParserTest {

  private final RegistrationRequestParser parser = new RegistrationRequestParser();

  private RegistrationInput parse(String json) {
    return parser.parse(json.getBytes(StandardCharsets.UTF_8));
  }

  private List<String> rejection(String json) {
    ValidationFailedException failure =
        catchThrowableOfType(ValidationFailedException.class, () -> parse(json));
    assertThat(failure).as("the body should be rejected").isNotNull();
    return failure.errors().stream().map(error -> error.field() + ":" + error.code()).toList();
  }

  private static List<String> shapeErrors(RegistrationInput input) {
    return input.shapeErrors().stream().map(error -> error.field() + ":" + error.code()).toList();
  }

  @Test
  void wellFormedBodyIsReadWithoutShapeErrors() {
    RegistrationInput input =
        parse(
            """
            {"type":"STUDENT","firstName":" Žan ","lastName":"Košir","email":"zan@example.org",
             "studyInstitution":"UL","studyProgramme":"RI","studentId":"1",
             "optionIds":["a","b"],"consent":true,"captchaToken":"t"}
            """);

    assertThat(input.type()).isEqualTo(RegistrationType.STUDENT);
    assertThat(input.texts().get(TextField.FIRST_NAME)).isEqualTo(" Žan ");
    assertThat(input.optionIds()).containsExactly("a", "b");
    assertThat(input.consent()).isTrue();
    assertThat(input.captchaToken()).isEqualTo("t");
    assertThat(input.shapeErrors()).isEmpty();
    assertThat(input.malformedFields()).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"", "not json", "[]", "\"text\"", "null", "{\"type\":\"EXTERNAL\"} trailing", "{"})
  void bodyThatIsNotOneJsonObjectIsMalformed(String body) {
    assertThat(rejection(body)).containsExactly("body:malformed");
  }

  @Test
  void missingTypeIsRequiredAndUnknownTypeIsInvalid() {
    assertThat(rejection("{}")).containsExactly("type:required");
    assertThat(rejection("{\"type\":null}")).containsExactly("type:required");
    assertThat(rejection("{\"type\":\"VIP\"}")).containsExactly("type:invalid_type");
    assertThat(rejection("{\"type\":\"external\"}")).containsExactly("type:invalid_type");
    assertThat(rejection("{\"type\":1}")).containsExactly("type:invalid_type");
  }

  @Test
  void valuesOfTheWrongKindAreReportedPerField() {
    RegistrationInput input =
        parse(
            """
            {"type":"EXTERNAL","firstName":5,"lastName":["x"],"email":{"a":1},"organization":true,
             "optionIds":"ws-testing","consent":"true","captchaToken":7}
            """);

    assertThat(shapeErrors(input))
        .containsExactlyInAnyOrder(
            "firstName:invalid_type",
            "lastName:invalid_type",
            "email:invalid_type",
            "organization:invalid_type",
            "optionIds:invalid_type",
            "consent:invalid_type",
            "captchaToken:invalid_type");
    assertThat(input.malformedFields())
        .containsExactlyInAnyOrder(
            "firstName",
            "lastName",
            "email",
            "organization",
            "optionIds",
            "consent",
            "captchaToken");
    assertThat(input.texts()).isEmpty();
    assertThat(input.optionIds()).isNull();
    assertThat(input.consent()).isNull();
  }

  @Test
  void optionListWithAnElementThatIsNotTextIsInvalid() {
    RegistrationInput input = parse("{\"type\":\"EXTERNAL\",\"optionIds\":[\"a\",2]}");

    assertThat(shapeErrors(input)).containsExactly("optionIds:invalid_type");
    assertThat(input.optionIds()).isNull();
  }

  @Test
  void absentAndNullValuesAreLeftForTheRules() {
    RegistrationInput input =
        parse("{\"type\":\"EXTERNAL\",\"firstName\":null,\"consent\":null,\"optionIds\":null}");

    assertThat(input.shapeErrors()).isEmpty();
    assertThat(input.texts()).isEmpty();
    assertThat(input.optionIds()).isNull();
    assertThat(input.consent()).isNull();
    assertThat(input.captchaToken()).isNull();
  }

  @Test
  void unknownPropertiesAreReportedWithoutEchoingTheirName() {
    RegistrationInput input =
        parse(
            """
            {"type":"EXTERNAL","studentId":"1","isAdmin":true,"<script>":"x"}
            """);

    // A field of the other form is named; any other name is reported as "body" (ES-07).
    assertThat(shapeErrors(input))
        .containsExactlyInAnyOrder(
            "studentId:unknown_field", "body:unknown_field", "body:unknown_field");
  }
}

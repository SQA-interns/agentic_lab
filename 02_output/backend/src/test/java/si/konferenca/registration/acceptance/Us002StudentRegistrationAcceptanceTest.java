package si.konferenca.registration.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;

/** US-002 Student registration, through the REST contract. */
class Us002StudentRegistrationAcceptanceTest extends AcceptanceTestBase {

  @Test
  void ac_002_01_validStudentRegistrationIsAccepted() {
    UUID id = assertAccepted(Api.register(Stack.app(), student()));

    assertThat(registrationRow(id).get("type")).isEqualTo("STUDENT");
  }

  static Stream<Arguments> emptyStudentFields() {
    return STUDENT_FIELDS.stream()
        .flatMap(field -> Stream.of(Arguments.of(field, ""), Arguments.of(field, "   ")));
  }

  @ParameterizedTest(name = "{0} = \"{1}\"")
  @MethodSource("emptyStudentFields")
  void ac_002_03_emptyOrBlankRequiredFieldIsRejected(String field, String value) {
    Api.Reply reply = Api.register(Stack.app(), with(student(), field, value));

    assertRejected(reply, field, "required");
    assertNothingStoredOrSent();
  }

  @Test
  void ac_002_04_surroundingWhitespaceIsNotStored() {
    Map<String, Object> registration = student();
    registration.put("firstName", " Luka  ");
    registration.put("lastName", "  Kovač ");
    registration.put("email", " luka.kovac@example.org  ");
    registration.put("studyInstitution", "  Univerza v Ljubljani ");
    registration.put("studyProgramme", " Računalništvo in informatika  ");
    registration.put("studentId", "  63210001 ");

    UUID id = assertAccepted(Api.register(Stack.app(), registration));

    Map<String, Object> row = registrationRow(id);
    assertThat(row.get("first_name")).isEqualTo("Luka");
    assertThat(row.get("last_name")).isEqualTo("Kovač");
    assertThat(row.get("email")).isEqualTo("luka.kovac@example.org");
    assertThat(row.get("study_institution")).isEqualTo("Univerza v Ljubljani");
    assertThat(row.get("study_programme")).isEqualTo("Računalništvo in informatika");
    assertThat(row.get("student_id")).isEqualTo("63210001");
  }

  @ParameterizedTest
  @ValueSource(strings = {"luka.kovac", "luka@", "@example.org", "luka kovac@example.org"})
  void ac_002_05_invalidEmailIsRejected(String email) {
    Api.Reply reply = Api.register(Stack.app(), with(student(), "email", email));

    assertRejected(reply, "email", "invalid_format");
    assertNothingStoredOrSent();
  }

  @Test
  void ac_002_06_slovenianCharactersAreKeptUnchanged() {
    Map<String, Object> registration = student();
    registration.put("firstName", "Žan");
    registration.put("lastName", "Šuštar Čeh");
    registration.put("studyInstitution", "Fakulteta za računalništvo, Univerza v Mariboru – Žalec");
    registration.put("studyProgramme", "Računalništvo in spletne tehnologije (š, č, ž)");

    UUID id = assertAccepted(Api.register(Stack.app(), registration));

    Map<String, Object> row = registrationRow(id);
    assertThat(row.get("first_name")).isEqualTo("Žan");
    assertThat(row.get("last_name")).isEqualTo("Šuštar Čeh");
    assertThat(row.get("study_institution"))
        .isEqualTo("Fakulteta za računalništvo, Univerza v Mariboru – Žalec");
    assertThat(row.get("study_programme"))
        .isEqualTo("Računalništvo in spletne tehnologije (š, č, ž)");
  }

  @ParameterizedTest
  @ValueSource(strings = {"no-such-option", "ws-legacy"})
  void ac_002_07_unknownOrInactiveOptionIsRejected(String optionId) {
    Api.Reply reply =
        Api.register(Stack.app(), with(student(), "optionIds", List.of("ev-opening", optionId)));

    assertRejected(reply, "optionIds", "option_not_selectable");
    assertNothingStoredOrSent();
  }

  @Test
  void ac_002_08_missingConsentIsRejected() {
    Api.Reply reply = Api.register(Stack.app(), with(student(), "consent", false));

    assertRejected(reply, "consent", "consent_required");
    assertNothingStoredOrSent();
  }

  @Test
  void ac_002_09_failedAntiAutomationCheckIsRejected() {
    Api.Reply reply =
        Api.register(Stack.app(), with(student(), "captchaToken", "not-the-passing-token"));

    assertRejected(reply, "captchaToken", "captcha_failed");
    assertNothingStoredOrSent();
  }

  @Test
  void ac_002_09_missingAntiAutomationCheckIsRejected() {
    Map<String, Object> registration = student();
    registration.remove("captchaToken");

    Api.Reply reply = Api.register(Stack.app(), registration);

    assertThat(reply.status()).isEqualTo(400);
    assertThat(reply.errorFields()).contains("captchaToken");
    assertNothingStoredOrSent();
  }

  @Test
  void ac_002_10_studentMaySelectEveryActiveOption() {
    Api.Reply options = Api.get(Stack.app(), "/api/options");
    assertThat(options.status()).isEqualTo(200);
    List<String> activeIds = new ArrayList<>();
    for (JsonNode option : options.json().path("options")) {
      activeIds.add(option.path("id").asString());
    }
    assertThat(activeIds).hasSize(7);

    UUID id = assertAccepted(Api.register(Stack.app(), with(student(), "optionIds", activeIds)));

    assertThat(optionRows(id))
        .extracting(row -> row.get("option_id"))
        .containsExactlyElementsOf(activeIds);
  }

  @Test
  void ac_002_02_externalFieldOnStudentRegistrationIsRejected() {
    Api.Reply reply = Api.register(Stack.app(), with(student(), "organization", "Podjetje Primer"));

    assertRejected(reply, "organization", "unknown_field");
    assertNothingStoredOrSent();
  }
}

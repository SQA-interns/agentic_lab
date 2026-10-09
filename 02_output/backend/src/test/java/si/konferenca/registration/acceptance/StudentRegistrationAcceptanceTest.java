package si.konferenca.registration.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static si.konferenca.registration.acceptance.support.Registrations.consents;
import static si.konferenca.registration.acceptance.support.Registrations.external;
import static si.konferenca.registration.acceptance.support.Registrations.letters;
import static si.konferenca.registration.acceptance.support.Registrations.options;
import static si.konferenca.registration.acceptance.support.Registrations.student;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import si.konferenca.registration.acceptance.support.AcceptanceTest;
import si.konferenca.registration.acceptance.support.ApiClient;
import tools.jackson.databind.node.ObjectNode;

/** US-002 Student registration, through the REST API. */
class StudentRegistrationAcceptanceTest extends AcceptanceTest {

  @Test
  void ac_002_01_validStudentRegistrationIsAcceptedWithExactlyTheGivenDataAndOptions() {
    ObjectNode request = options(student(), "ws-security", "meal-lunch-2", "other-career-fair");

    ApiClient.Response response = api.register(request);

    assertThat(response.status()).as(response.text()).isEqualTo(201);
    assertThat(response.json().path("type").asString()).isEqualTo("STUDENT");
    String id = response.json().path("registrationId").asString();
    assertThat(UUID.fromString(id)).isNotNull();
    Map<String, Object> row =
        db.registrationByEmail(request.path("email").asString()).orElseThrow();
    assertThat(row.get("id").toString()).isEqualTo(id);
    assertThat(row.get("type")).isEqualTo("STUDENT");
    assertThat(row.get("first_name")).isEqualTo("Luka");
    assertThat(row.get("last_name")).isEqualTo("Kranjc");
    assertThat(row.get("email")).isEqualTo(request.path("email").asString());
    assertThat(row.get("organization")).isNull();
    assertThat(row.get("study_institution")).isEqualTo("Fakulteta za računalništvo in informatiko");
    assertThat(row.get("study_programme")).isEqualTo("Računalništvo in informatika");
    assertThat(row.get("student_id")).isEqualTo("63200001");
    assertThat(db.options(row.get("id")))
        .extracting(option -> option.get("option_id"))
        .containsExactly("meal-lunch-2", "other-career-fair", "ws-security");
  }

  @ParameterizedTest(name = "AC-002-03 {0} = \"{1}\"")
  @CsvSource(
      value = {
        "firstName|''",
        "lastName|'   '",
        "email|''",
        "studyInstitution|''",
        "studyInstitution|'   '",
        "studyProgramme|''",
        "studyProgramme|'  '",
        "studentId|''",
        "studentId|'    '"
      },
      delimiter = '|')
  void ac_002_03_emptyOrBlankRequiredFieldIsRejectedNamingTheField(String field, String value) {
    ObjectNode request = student();
    request.put(field, value);

    ApiClient.Response response = api.register(request);

    assertFieldError(response, field, "required");
    assertNothingStoredOrSent();
  }

  @ParameterizedTest(name = "AC-002-03 {0} absent")
  @ValueSource(
      strings = {
        "firstName",
        "lastName",
        "email",
        "studyInstitution",
        "studyProgramme",
        "studentId"
      })
  void ac_002_03_absentRequiredFieldIsRejectedNamingTheField(String field) {
    ObjectNode request = student();
    request.remove(field);

    ApiClient.Response response = api.register(request);

    assertFieldError(response, field, "required");
    assertNothingStoredOrSent();
  }

  @Test
  void ac_002_04_leadingAndTrailingWhitespaceIsNotStored() {
    ObjectNode request = student();
    String email = request.path("email").asString();
    request.put("email", " " + email + "  ");
    request.put("studyInstitution", "  Fakulteta za matematiko in fiziko ");
    request.put("studyProgramme", " Matematika\t");
    request.put("studentId", "  27190001 ");

    ApiClient.Response response = api.register(request);

    assertThat(response.status()).as(response.text()).isEqualTo(201);
    Map<String, Object> row = db.registrationByEmail(email).orElseThrow();
    assertThat(row.get("email")).isEqualTo(email);
    assertThat(row.get("study_institution")).isEqualTo("Fakulteta za matematiko in fiziko");
    assertThat(row.get("study_programme")).isEqualTo("Matematika");
    assertThat(row.get("student_id")).isEqualTo("27190001");
  }

  @ParameterizedTest(name = "AC-002-05 email \"{0}\"")
  @ValueSource(strings = {"luka", "luka@fri", "luka kranjc@student.uni-lj.si", "luka@.si@x"})
  void ac_002_05_invalidEmailIsRejectedNamingTheEmailField(String email) {
    ObjectNode request = student();
    request.put("email", email);

    ApiClient.Response response = api.register(request);

    assertFieldError(response, "email", "invalid_email");
    assertNothingStoredOrSent();
  }

  @Test
  void ac_002_06_unicodeTextWithSlovenianCharactersIsStoredUnchanged() {
    ObjectNode request = student();
    request.put("firstName", "Žana");
    request.put("lastName", "Kovačič Šinkovec");
    request.put("studyInstitution", "Fakulteta za družbene vede");
    request.put("studyProgramme", "Komunikologija – smer Čšž");

    ApiClient.Response response = api.register(request);

    assertThat(response.status()).as(response.text()).isEqualTo(201);
    Map<String, Object> row =
        db.registrationByEmail(request.path("email").asString()).orElseThrow();
    assertThat(row.get("first_name")).isEqualTo("Žana");
    assertThat(row.get("last_name")).isEqualTo("Kovačič Šinkovec");
    assertThat(row.get("study_institution")).isEqualTo("Fakulteta za družbene vede");
    assertThat(row.get("study_programme")).isEqualTo("Komunikologija – smer Čšž");
  }

  @ParameterizedTest(name = "AC-002-07 option {0} gives {1}")
  @CsvSource({"no-such-option, unknown_option", "ws-legacy, inactive_option"})
  void ac_002_07_unknownOrInactiveOptionIsRejected(String optionId, String code) {
    ObjectNode request = options(student(), optionId);

    ApiClient.Response response = api.register(request);

    assertFieldError(response, "optionIds", code);
    assertNothingStoredOrSent();
  }

  @Test
  void ac_002_08_optionNotAvailableToStudentsIsRejected() {
    ObjectNode request = options(student(), "ev-industry-dinner");

    ApiClient.Response response = api.register(request);

    assertFieldError(response, "optionIds", "option_not_available");
    assertNothingStoredOrSent();
  }

  @Test
  void ac_002_09_moreOptionsInOneCategoryThanAllowedIsRejected() {
    ObjectNode request = options(student(), "ws-ai", "ws-security");

    ApiClient.Response response = api.register(request);

    assertFieldError(response, "optionIds", "too_many_options");
    assertNothingStoredOrSent();
  }

  @Test
  void ac_002_10_registrationWithoutTheMandatoryConsentIsRejected() {
    ObjectNode request = consents(student());

    ApiClient.Response response = api.register(request);

    assertFieldError(response, "consentIds", "consent_required");
    assertNothingStoredOrSent();
  }

  @Test
  void ac_002_12_registrationWithoutAValidAntiAutomationProofIsRejected() {
    ObjectNode request = student();
    request.put("captchaToken", "forged");

    ApiClient.Response response = api.register(request);

    assertThat(response.status()).as(response.text()).isEqualTo(400);
    assertThat(response.json().path("error").asString()).isEqualTo("captcha_failed");
    assertNothingStoredOrSent();
  }

  @Test
  void ac_002_13_studentRegistrationWithTheEmailOfAnExternalRegistrationIsRejected() {
    ObjectNode first = external();
    String email = first.path("email").asString();
    assertThat(api.register(first).status()).isEqualTo(201);
    mail.clear();

    ObjectNode second = student();
    second.put("email", email);
    ApiClient.Response response = api.register(second);

    assertThat(response.status()).as(response.text()).isEqualTo(409);
    assertThat(response.json().path("error").asString()).isEqualTo("duplicate_email");
    assertThat(db.countRegistrations()).isEqualTo(1);
    assertThat(db.registrationByEmail(email).orElseThrow().get("type")).isEqualTo("EXTERNAL");
    assertNoEmail();
  }

  @ParameterizedTest(name = "AC-002-14 {0} longer than {1}")
  @CsvSource({
    "firstName, 100",
    "lastName, 100",
    "studyInstitution, 200",
    "studyProgramme, 200",
    "studentId, 50"
  })
  void ac_002_14_textFieldLongerThanItsMaximumIsRejected(String field, int maximum) {
    ObjectNode request = student();
    request.put(field, letters(maximum + 1));

    ApiClient.Response response = api.register(request);

    assertFieldError(response, field, "too_long");
    assertNothingStoredOrSent();
  }

  @Test
  void ac_002_14_textFieldsOfExactlyTheMaximumLengthAreAccepted() {
    ObjectNode request = student();
    request.put("studyInstitution", letters(200));
    request.put("studyProgramme", letters(200));
    request.put("studentId", letters(50));

    ApiClient.Response response = api.register(request);

    assertThat(response.status()).as(response.text()).isEqualTo(201);
  }

  @ParameterizedTest(name = "AC-002-16 {0} with a control character")
  @ValueSource(strings = {"studyInstitution", "studyProgramme", "studentId", "lastName"})
  void ac_002_16_controlCharacterInATextFieldIsRejected(String field) {
    ObjectNode request = student();
    request.put(field, "line one\nline two");

    ApiClient.Response response = api.register(request);

    assertFieldError(response, field, "invalid_characters");
    assertNothingStoredOrSent();
  }
}

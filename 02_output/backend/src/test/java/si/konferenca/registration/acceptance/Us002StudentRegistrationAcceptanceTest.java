package si.konferenca.registration.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static si.konferenca.registration.acceptance.support.Registrations.student;
import static si.konferenca.registration.acceptance.support.Registrations.with;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import si.konferenca.registration.acceptance.support.AcceptanceStack;
import si.konferenca.registration.acceptance.support.Api;
import si.konferenca.registration.acceptance.support.Response;
import si.konferenca.registration.acceptance.support.Storage;
import tools.jackson.databind.JsonNode;

/** US-002 Student registration, through the REST API. */
class Us002StudentRegistrationAcceptanceTest {

  private final Api api = AcceptanceStack.shared().api();

  @Test
  @DisplayName("AC-002-01 a complete student registration is accepted")
  void ac002_01_validStudentRegistrationIsAccepted() {
    Map<String, Object> request = student();

    Response response = api.register(request);

    assertThat(response.status()).as(response.toString()).isEqualTo(201);
    UUID id = UUID.fromString(response.json().path("id").asString());
    assertThat(AcceptanceStack.db().registrationById(id).orElseThrow())
        .containsEntry("type", "STUDENT")
        .containsEntry("study_institution", "Univerza v Mariboru")
        .containsEntry("study_programme", "Informatika")
        .containsEntry("student_id", "E1234567")
        .containsEntry("organization", null);
  }

  @ParameterizedTest(name = "AC-002-03 required field {0} with value [{1}] is rejected")
  @CsvSource(
      delimiter = '|',
      value = {
        "firstName|''",
        "lastName|''",
        "email|''",
        "studyInstitution|''",
        "studyProgramme|''",
        "studentId|''",
        "studyInstitution|' '",
        "studyProgramme|'   '",
        "studentId|' \t'"
      })
  void ac002_03_emptyOrBlankRequiredFieldIsRejected(String field, String value) {
    Storage.Snapshot before = Storage.snapshot();

    Response response = api.register(with(student(), field, value));

    assertThat(response.hasFieldError(field, "required")).as(response.toString()).isTrue();
    before.assertUnchanged();
  }

  @ParameterizedTest(name = "AC-002-04 invalid email [{0}] is rejected")
  @ValueSource(strings = {"luka.horvat", "luka@", "luka@student", "luka horvat@um.si"})
  void ac002_04_invalidEmailIsRejected(String email) {
    Storage.Snapshot before = Storage.snapshot();

    Response response = api.register(with(student(), "email", email));

    assertThat(response.hasFieldError("email", "invalid_email")).as(response.toString()).isTrue();
    before.assertUnchanged();
  }

  @Test
  @DisplayName("AC-002-05 Slovenian characters in student fields are stored unchanged")
  void ac002_05_unicodeStudentFieldsAreStoredUnchanged() {
    Map<String, Object> request = student();
    request.put("firstName", "Žiga");
    request.put("lastName", "Kovačič");
    request.put("studyInstitution", "Univerza v Ljubljani, Fakulteta za računalništvo");
    request.put("studyProgramme", "Računalništvo in informatika – smer Š");

    Response response = api.register(request);

    assertThat(response.status()).as(response.toString()).isEqualTo(201);
    UUID id = UUID.fromString(response.json().path("id").asString());
    assertThat(AcceptanceStack.db().registrationById(id).orElseThrow())
        .containsEntry("first_name", "Žiga")
        .containsEntry("last_name", "Kovačič")
        .containsEntry("study_institution", "Univerza v Ljubljani, Fakulteta za računalništvo")
        .containsEntry("study_programme", "Računalništvo in informatika – smer Š");
  }

  @Test
  @DisplayName("AC-002-06 an option not available to students is marked as such")
  void ac002_06_formMarksOptionsNotAvailableToStudents() {
    Response response = api.getForm();

    assertThat(response.status()).as(response.toString()).isEqualTo(200);
    assertThat(types(response.json(), "ws-security")).containsExactly("EXTERNAL");
    assertThat(types(response.json(), "ev-career-fair")).containsExactly("STUDENT");
    assertThat(types(response.json(), "ws-testing"))
        .containsExactlyInAnyOrder("EXTERNAL", "STUDENT");
  }

  @Test
  @DisplayName("AC-002-07 a student selecting an option not available to students is rejected")
  void ac002_07_optionNotAvailableToStudentsIsRejected() {
    Storage.Snapshot before = Storage.snapshot();

    Response response = api.register(with(student(), "optionIds", List.of("ws-security")));

    assertThat(response.hasFieldError("optionIds", "option_not_available"))
        .as(response.toString())
        .isTrue();
    before.assertUnchanged();
  }

  @ParameterizedTest(name = "AC-002-08 option {0} is rejected with {1}")
  @CsvSource({"ws-legacy,inactive_option", "no-such-option,unknown_option"})
  void ac002_08_inactiveOrUnknownOptionIsRejected(String optionId, String code) {
    Storage.Snapshot before = Storage.snapshot();

    Response response = api.register(with(student(), "optionIds", List.of(optionId)));

    assertThat(response.hasFieldError("optionIds", code)).as(response.toString()).isTrue();
    before.assertUnchanged();
  }

  @Test
  @DisplayName("AC-002-09 a student registration without the mandatory consent is rejected")
  void ac002_09_missingMandatoryConsentIsRejected() {
    Storage.Snapshot before = Storage.snapshot();

    Response response = api.register(with(student(), "consentIds", List.of()));

    assertThat(response.hasFieldError("consentIds", "consent_missing"))
        .as(response.toString())
        .isTrue();
    before.assertUnchanged();
  }

  @Test
  @DisplayName("AC-002-10 a student registration failing the anti-automation check is rejected")
  void ac002_10_failedAntiAutomationCheckIsRejected() {
    Storage.Snapshot before = Storage.snapshot();

    Response response = api.register(with(student(), "recaptchaToken", "forged-token"));

    assertThat(response.hasFieldError("recaptchaToken", "captcha_failed"))
        .as(response.toString())
        .isTrue();
    before.assertUnchanged();
  }

  private static List<String> types(JsonNode form, String optionId) {
    List<String> types = new ArrayList<>();
    for (JsonNode type :
        Us001ExternalRegistrationAcceptanceTest.optionById(form, optionId)
            .path("registrationTypes")) {
      types.add(type.asString());
    }
    return types;
  }
}

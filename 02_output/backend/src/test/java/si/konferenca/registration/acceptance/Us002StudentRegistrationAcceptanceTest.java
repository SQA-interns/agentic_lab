package si.konferenca.registration.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static si.konferenca.registration.acceptance.support.Problems.assertFieldError;
import static si.konferenca.registration.acceptance.support.Registrations.CONSENT_DATA;
import static si.konferenca.registration.acceptance.support.Registrations.OPTION_EXTERNAL_ONLY;
import static si.konferenca.registration.acceptance.support.Registrations.OPTION_INACTIVE;
import static si.konferenca.registration.acceptance.support.Registrations.OPTION_OTHER;
import static si.konferenca.registration.acceptance.support.Registrations.OPTION_WORKSHOP;
import static si.konferenca.registration.acceptance.support.Registrations.external;
import static si.konferenca.registration.acceptance.support.Registrations.student;
import static si.konferenca.registration.acceptance.support.Registrations.withConsents;
import static si.konferenca.registration.acceptance.support.Registrations.withOptions;

import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import si.konferenca.registration.acceptance.support.AcceptanceTestBase;
import si.konferenca.registration.acceptance.support.Api;
import si.konferenca.registration.acceptance.support.Database;
import si.konferenca.registration.acceptance.support.JsonCopies;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/** US-002 Student registration, through the REST API. */
class Us002StudentRegistrationAcceptanceTest extends AcceptanceTestBase {

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    configure(registry, Map.of());
  }

  @Test
  void ac002_01_studentFormAcceptsOnlyTheStudentFields() {
    ObjectNode request = student();
    request.put("organization", "Institut Jozef Stefan");

    assertFieldError(api.register(request), 400, "organization", "NOT_ALLOWED");
    assertNothingStored();
  }

  @Test
  void ac002_02_validStudentRegistrationIsAcceptedWithSelectedOptions() {
    Api.Response response = api.register(student());

    assertThat(response.status()).as("body: %s", response.text()).isEqualTo(201);
    JsonNode body = response.json();
    assertThat(body.path("type").asString()).isEqualTo("student");
    assertThat(body.path("options").findValuesAsString("id"))
        .containsExactlyInAnyOrder(OPTION_WORKSHOP, OPTION_OTHER);
    Map<String, Object> row =
        Database.rows(
                "SELECT type, study_institution, study_programme, student_id, organization"
                    + " FROM registration WHERE id = ?::uuid",
                body.path("registrationId").asString())
            .get(0);
    assertThat(row)
        .containsEntry("type", "student")
        .containsEntry("study_institution", "Univerza v Mariboru")
        .containsEntry("study_programme", "Informatika")
        .containsEntry("student_id", "93120045")
        .containsEntry("organization", null);
  }

  static Stream<Arguments> emptyStudentFields() {
    return Stream.of(
            "firstName", "lastName", "email", "studyInstitution", "studyProgramme", "studentId")
        .flatMap(field -> Stream.of(Arguments.of(field, ""), Arguments.of(field, "   ")));
  }

  @ParameterizedTest
  @MethodSource("emptyStudentFields")
  void ac002_03_emptyOrBlankStudentFieldIsRejectedAtThatField(String field, String value) {
    ObjectNode request = student();
    request.put(field, value);

    assertFieldError(api.register(request), 400, field, "REQUIRED");
    assertNothingStored();
  }

  @ParameterizedTest
  @ValueSource(strings = {"luka", "luka@student", "luka.kranjc@", "luka kranjc@student.si"})
  void ac002_04_invalidEmailIsRejectedAtTheEmailField(String email) {
    ObjectNode request = student();
    request.put("email", email);

    assertFieldError(api.register(request), 400, "email", "INVALID_EMAIL");
    assertNothingStored();
  }

  @Test
  void ac002_05_slovenianStudyInstitutionAndProgrammeAreStoredUnchanged() {
    ObjectNode request = student();
    request.put("studyInstitution", "Univerza v Ljubljani, Fakulteta za računalništvo");
    request.put("studyProgramme", "Računalništvo in matematika, šolsko leto 2026/27");

    Api.Response response = api.register(request);

    assertThat(response.status()).as("body: %s", response.text()).isEqualTo(201);
    String id = response.json().path("registrationId").asString();
    Map<String, Object> row =
        Database.rows(
                "SELECT study_institution, study_programme FROM registration WHERE id = ?::uuid",
                id)
            .get(0);
    assertThat(row)
        .containsEntry("study_institution", "Univerza v Ljubljani, Fakulteta za računalništvo")
        .containsEntry("study_programme", "Računalništvo in matematika, šolsko leto 2026/27");
    assertThat(JsonCopies.read(id).path("participant").path("studyProgramme").asString())
        .isEqualTo("Računalništvo in matematika, šolsko leto 2026/27");
  }

  @Test
  void ac002_06_unknownOptionIsRejected() {
    assertFieldError(
        api.register(withOptions(student(), "meal-unknown")), 400, "optionIds", "UNKNOWN_OPTION");
    assertNothingStored();
  }

  @Test
  void ac002_06_inactiveOptionIsRejected() {
    assertFieldError(
        api.register(withOptions(student(), OPTION_INACTIVE)), 400, "optionIds", "INACTIVE_OPTION");
    assertNothingStored();
  }

  @Test
  void ac002_07_optionOfferedOnlyToExternalParticipantsIsRejectedForStudents() {
    assertFieldError(
        api.register(withOptions(student(), OPTION_WORKSHOP, OPTION_EXTERNAL_ONLY)),
        400,
        "optionIds",
        "OPTION_NOT_OFFERED");
    assertNothingStored();
  }

  @Test
  void ac002_08_missingMandatoryConsentIsRejectedAtThatConsent() {
    assertFieldError(
        api.register(withConsents(student())), 400, "consents." + CONSENT_DATA, "CONSENT_REQUIRED");
    assertNothingStored();
  }

  @Test
  void ac002_09_rejectedAntiAutomationTokenIsRefusedAndNothingStored() {
    ObjectNode request = student();
    request.put("captchaToken", "robot");

    assertFieldError(api.register(request), 400, "captchaToken", "CAPTCHA_FAILED");
    assertNothingStored();
  }

  @Test
  void ac002_10_studentWithEmailOfAnExistingRegistrationIsRejected() {
    ObjectNode first = external();
    first.put("email", "shared.address@example.com");
    assertThat(api.register(first).status()).isEqualTo(201);
    ObjectNode request = student();
    request.put("email", "Shared.Address@example.com");

    Api.Response response = api.register(request);

    assertFieldError(response, 409, "email", "DUPLICATE_EMAIL");
    assertThat(response.text().toLowerCase(java.util.Locale.ROOT)).contains("organizer");
    assertThat(Database.registrationCount()).isEqualTo(1);
  }
}

package si.konferenca.registration.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import si.konferenca.registration.acceptance.support.AcceptanceTestBase;
import si.konferenca.registration.acceptance.support.ApiClient;
import si.konferenca.registration.acceptance.support.Registrations;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/** US-002 Student registration. */
class Us002StudentRegistrationAcceptanceTest extends AcceptanceTestBase {

  @Test
  @DisplayName("AC-002-01 a valid student registration with active options is accepted")
  void ac002_01_validStudentRegistrationIsAccepted() {
    ObjectNode registration =
        Registrations.withOptions(Registrations.student(), "ws-security", "ev-reception");
    ApiClient.Response response = api.register(registration);

    assertThat(response.status()).as(response.text()).isEqualTo(201);
    JsonNode body = response.json();
    assertThat(body.get("type").asString()).isEqualTo("STUDENT");
    UUID id = idOf(body);
    Map<String, Object> row =
        database.registrationByEmail(Registrations.email(registration)).orElseThrow();
    assertThat(row.get("id")).isEqualTo(id);
    assertThat(row.get("type")).isEqualTo("STUDENT");
    assertThat(row.get("study_institution")).isEqualTo("Univerza v Ljubljani");
    assertThat(row.get("study_programme")).isEqualTo("Računalništvo in informatika");
    assertThat(row.get("student_id")).isEqualTo("63210042");
    assertThat(row.get("organization")).isNull();
    assertThat(database.options(id))
        .extracting(o -> o.get("option_id"))
        .containsExactly("ws-security", "ev-reception");
  }

  @Test
  @DisplayName("AC-002-02 the student registration takes exactly the student fields")
  void ac002_02_studentRegistrationTakesOnlyStudentFields() {
    ObjectNode withOrganization = Registrations.student();
    withOrganization.put("organization", "Podjetje d.o.o.");
    ApiClient.Response response = api.register(withOrganization);

    assertFieldError(response, "organization", "NOT_ALLOWED");
    assertNothingStoredFor(Registrations.email(withOrganization));
  }

  @ParameterizedTest(name = "AC-002-03 required field {0} empty, blank or missing is rejected")
  @ValueSource(
      strings = {
        "firstName",
        "lastName",
        "email",
        "studyInstitution",
        "studyProgramme",
        "studentId"
      })
  void ac002_03_missingRequiredFieldIsRejected(String field) {
    for (String variant : new String[] {"", "  ", null}) {
      ObjectNode registration = Registrations.student();
      String email = Registrations.email(registration);
      if (variant == null) {
        registration.remove(field);
      } else {
        registration.put(field, variant);
      }
      ApiClient.Response response = api.register(registration);

      assertFieldError(response, field, "REQUIRED");
      assertNothingStoredFor(email);
    }
  }

  @Test
  @DisplayName("AC-002-04 surrounding whitespace is not stored")
  void ac002_04_surroundingWhitespaceIsTrimmed() {
    ObjectNode registration = Registrations.student();
    String email = Registrations.email(registration);
    registration.put("email", "\t" + email + "  ");
    registration.put("studyInstitution", "  Univerza v Ljubljani ");
    registration.put("studyProgramme", " Fizika ");
    registration.put("studentId", " 28190001\t");

    registerAccepted(registration);

    Map<String, Object> row = database.registrationByEmail(email).orElseThrow();
    assertThat(row.get("email")).isEqualTo(email);
    assertThat(row.get("study_institution")).isEqualTo("Univerza v Ljubljani");
    assertThat(row.get("study_programme")).isEqualTo("Fizika");
    assertThat(row.get("student_id")).isEqualTo("28190001");
  }

  @ParameterizedTest(name = "AC-002-05 invalid email \"{0}\" is rejected")
  @ValueSource(strings = {"luka", "luka@kranjc", "luka@ example.si", "luka.kranjc@example."})
  void ac002_05_invalidEmailIsRejected(String invalid) {
    ObjectNode registration = Registrations.student();
    registration.put("email", invalid);
    ApiClient.Response response = api.register(registration);

    assertFieldError(response, "email", "INVALID_EMAIL");
    assertThat(database.countByEmail(invalid)).isZero();
  }

  @Test
  @DisplayName("AC-002-06 Slovenian and other Unicode letters are stored unchanged")
  void ac002_06_unicodeIsStoredUnchanged() {
    ObjectNode registration = Registrations.student();
    registration.put("firstName", "Žiga");
    registration.put("lastName", "Čebašek Šuštaršič");
    registration.put("studyInstitution", "Fakulteta za računalništvo in informatiko");
    registration.put("studyProgramme", "Računalništvo – magistrski študij (ČŠŽ čšž)");

    registerAccepted(registration);

    Map<String, Object> row =
        database.registrationByEmail(Registrations.email(registration)).orElseThrow();
    assertThat(row.get("first_name")).isEqualTo("Žiga");
    assertThat(row.get("last_name")).isEqualTo("Čebašek Šuštaršič");
    assertThat(row.get("study_institution")).isEqualTo("Fakulteta za računalništvo in informatiko");
    assertThat(row.get("study_programme")).isEqualTo("Računalništvo – magistrski študij (ČŠŽ čšž)");
  }

  @Test
  @DisplayName("AC-002-07 a student registration without the mandatory consent is rejected")
  void ac002_07_missingConsentIsRejected() {
    ObjectNode registration = Registrations.student();
    registration.put("consentGiven", false);
    ApiClient.Response response = api.register(registration);

    assertFieldError(response, "consentGiven", "CONSENT_REQUIRED");
    assertNothingStoredFor(Registrations.email(registration));
  }

  @Test
  @DisplayName("AC-002-08 students can select every active option offered by the form")
  void ac002_08_studentsCanSelectEveryActiveOption() {
    ApiClient.Response config = api.formConfig();
    assertThat(config.status()).isEqualTo(200);
    List<String> active = new ArrayList<>();
    config.json().get("options").forEach(o -> active.add(o.get("id").asString()));
    assertThat(active).isNotEmpty();

    ObjectNode registration =
        Registrations.withOptions(Registrations.student(), active.toArray(String[]::new));
    UUID id = idOf(registerAccepted(registration));

    assertThat(database.options(id))
        .extracting(o -> o.get("option_id"))
        .containsExactlyElementsOf(active);
  }

  @Test
  @DisplayName("AC-002-09 a student registration with an already registered email is rejected")
  void ac002_09_duplicateEmailIsRejected() {
    ObjectNode first = Registrations.external();
    String email = Registrations.email(first);
    registerAccepted(first);

    ObjectNode second = Registrations.student();
    second.put("email", " " + email.toUpperCase(java.util.Locale.ROOT));
    ApiClient.Response response = api.register(second);

    assertThat(response.status()).as(response.text()).isEqualTo(409);
    JsonNode problem = response.json();
    assertThat(problem.get("code").asString()).isEqualTo("EMAIL_ALREADY_REGISTERED");
    assertThat(problem.get("detail").asString()).containsIgnoringCase("already registered");
    assertThat(problem.get("detail").asString()).containsIgnoringCase("organizers");
    assertThat(database.countByEmail(email)).isEqualTo(1);
    assertThat(database.registrationByEmail(email).orElseThrow().get("type")).isEqualTo("EXTERNAL");
  }

  @Test
  @DisplayName("AC-002-10 unknown or inactive options are rejected for students")
  void ac002_10_unknownOrInactiveOptionIsRejected() {
    ObjectNode unknown = Registrations.withOptions(Registrations.student(), "ws-unknown");
    assertFieldError(api.register(unknown), "optionIds", "UNKNOWN_OPTION");
    assertNothingStoredFor(Registrations.email(unknown));

    ObjectNode inactive = Registrations.withOptions(Registrations.student(), "ws-legacy");
    assertFieldError(api.register(inactive), "optionIds", "INACTIVE_OPTION");
    assertNothingStoredFor(Registrations.email(inactive));
  }

  @Test
  @DisplayName("AC-002-11 a failed or missing anti-automation check is rejected for students")
  void ac002_11_failedOrMissingCaptchaIsRejected() {
    ObjectNode failed = Registrations.student();
    failed.put("captchaToken", "forged");
    assertFieldError(api.register(failed), "captchaToken", "CAPTCHA_FAILED");
    assertNothingStoredFor(Registrations.email(failed));

    ObjectNode missing = Registrations.student();
    missing.put("captchaToken", " ");
    assertFieldError(api.register(missing), "captchaToken", "REQUIRED");
    assertNothingStoredFor(Registrations.email(missing));
  }
}

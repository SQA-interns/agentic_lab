package si.konferenca.registration.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static si.konferenca.registration.acceptance.support.Payloads.external;
import static si.konferenca.registration.acceptance.support.Payloads.student;
import static si.konferenca.registration.acceptance.support.Payloads.with;
import static si.konferenca.registration.acceptance.support.Payloads.without;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import si.konferenca.registration.acceptance.support.AcceptanceTestBase;
import si.konferenca.registration.acceptance.support.Api;
import si.konferenca.registration.acceptance.support.Json;
import tools.jackson.databind.JsonNode;

/** US-002 Student registration, through the REST API. */
class Us002StudentRegistrationAcceptanceTest extends AcceptanceTestBase {

  private static final List<String> STUDENT_FIELDS =
      List.of("firstName", "lastName", "email", "studyInstitution", "studyProgramme", "studentId");

  @Test
  void AC_002_02_valid_student_registration_is_accepted_with_the_entered_values() {
    Map<String, Object> body = student();

    JsonNode accepted = assertAccepted(api.register(body));

    assertThat(accepted.path("type").asString()).isEqualTo("STUDENT");
    assertThat(accepted.path("firstName").asString()).isEqualTo("Ana");
    Map<String, Object> row = db.registrationByEmail((String) body.get("email"));
    assertThat(row).isNotNull();
    assertThat(row.get("type")).isEqualTo("STUDENT");
    assertThat(row.get("study_institution")).isEqualTo("University of Ljubljana");
    assertThat(row.get("study_programme")).isEqualTo("Computer Science");
    assertThat(row.get("student_id")).isEqualTo("63210001");
    assertThat(row.get("organization")).isNull();
  }

  @Test
  void AC_002_03_active_options_available_to_students_are_accepted_exactly() {
    Map<String, Object> body =
        with(student(), "optionIds", List.of("ws-open-data", "ev-welcome", "meal-vegetarian"));

    JsonNode accepted = assertAccepted(api.register(body));

    assertThat(Json.strings(accepted.path("options"), "id"))
        .containsExactlyInAnyOrder("ws-open-data", "ev-welcome", "meal-vegetarian");
  }

  @Test
  void AC_002_03_student_registration_without_options_is_accepted() {
    JsonNode accepted = assertAccepted(api.register(with(student(), "optionIds", List.of())));

    assertThat(accepted.path("options").size()).isZero();
  }

  @Test
  void AC_002_04_each_empty_or_missing_required_field_is_rejected() {
    for (String field : STUDENT_FIELDS) {
      assertFieldError(api.register(with(student(), field, "")), field, "REQUIRED");
      assertFieldError(api.register(with(student(), field, "  ")), field, "REQUIRED");
      assertFieldError(api.register(without(student(), field)), field, "REQUIRED");
    }
    assertNothingStored();
  }

  @Test
  void AC_002_05_leading_and_trailing_whitespace_is_not_stored() {
    Map<String, Object> body = student();
    String email = (String) body.get("email");
    body.put("email", " " + email + " ");
    body.put("studyInstitution", "  University of Ljubljana ");
    body.put("studyProgramme", " Computer Science  ");
    body.put("studentId", " 63210001 ");

    JsonNode accepted = assertAccepted(api.register(body));

    assertThat(accepted.path("email").asString()).isEqualTo(email);
    Map<String, Object> row = db.registrationByEmail(email);
    assertThat(row.get("study_institution")).isEqualTo("University of Ljubljana");
    assertThat(row.get("study_programme")).isEqualTo("Computer Science");
    assertThat(row.get("student_id")).isEqualTo("63210001");
  }

  @ParameterizedTest
  @ValueSource(strings = {"ana", "ana@", "ana horvat@example.com", "ana@example"})
  void AC_002_06_invalid_email_is_rejected(String email) {
    assertFieldError(api.register(with(student(), "email", email)), "email", "INVALID_FORMAT");
    assertNothingStored();
  }

  @Test
  void AC_002_07_slovenian_characters_are_kept_unchanged() {
    Map<String, Object> body = student();
    body.put("firstName", "Špela");
    body.put("lastName", "Žagar");
    body.put("studyInstitution", "Univerza v Ljubljani, Fakulteta za računalništvo");
    body.put("studyProgramme", "Računalništvo in informatika, smer Šolska");

    assertAccepted(api.register(body));

    Map<String, Object> row = db.registrationByEmail((String) body.get("email"));
    assertThat(row.get("first_name")).isEqualTo("Špela");
    assertThat(row.get("last_name")).isEqualTo("Žagar");
    assertThat(row.get("study_institution"))
        .isEqualTo("Univerza v Ljubljani, Fakulteta za računalništvo");
    assertThat(row.get("study_programme")).isEqualTo("Računalništvo in informatika, smer Šolska");
  }

  @Test
  void AC_002_08_unknown_option_is_rejected() {
    assertFieldError(
        api.register(with(student(), "optionIds", List.of("no-such-option"))),
        "optionIds",
        "UNKNOWN_OPTION");
    assertNothingStored();
  }

  @Test
  void AC_002_08_inactive_option_is_rejected() {
    assertFieldError(
        api.register(with(student(), "optionIds", List.of("ws-legacy"))),
        "optionIds",
        "INACTIVE_OPTION");
    assertNothingStored();
  }

  @ParameterizedTest
  @ValueSource(strings = {"ws-industry-lab", "ev-gala-dinner"})
  void AC_002_09_option_not_available_to_students_is_rejected(String optionId) {
    assertFieldError(
        api.register(with(student(), "optionIds", List.of(optionId))),
        "optionIds",
        "OPTION_NOT_AVAILABLE");
    assertNothingStored();
  }

  @Test
  void AC_002_10_student_registration_without_the_mandatory_consent_is_rejected() {
    assertFieldError(
        api.register(with(student(), "consentIds", List.of())), "consentIds", "CONSENT_MISSING");
    assertNothingStored();
  }

  @Test
  void AC_002_11_failed_anti_automation_check_is_rejected() {
    assertError(
        api.register(with(student(), "antiAutomationToken", "wrong-token")), 400, "CAPTCHA_FAILED");
    assertError(api.register(without(student(), "antiAutomationToken")), 400, "CAPTCHA_FAILED");
    assertNothingStored();
  }

  @Test
  void AC_002_12_email_registered_by_an_external_participant_is_rejected_for_a_student() {
    Map<String, Object> first = external();
    assertAccepted(api.register(first));

    Api.Response second = api.register(with(student(), "email", first.get("email")));

    assertError(second, 409, "DUPLICATE_EMAIL");
    assertThat(second.json().path("message").asString()).containsIgnoringCase("organizer");
    assertThat(db.countRegistrations()).isEqualTo(1);
  }

  @Test
  void AC_002_12_email_registered_by_a_student_is_rejected_for_a_second_student() {
    Map<String, Object> first = student();
    assertAccepted(api.register(first));

    Api.Response second =
        api.register(
            with(
                student(),
                "email",
                ((String) first.get("email")).toUpperCase(java.util.Locale.ROOT)));

    assertError(second, 409, "DUPLICATE_EMAIL");
    assertThat(db.countRegistrations()).isEqualTo(1);
  }
}

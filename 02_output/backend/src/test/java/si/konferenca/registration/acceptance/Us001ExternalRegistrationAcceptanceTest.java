package si.konferenca.registration.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

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

/** US-001 External participant registration. */
class Us001ExternalRegistrationAcceptanceTest extends AcceptanceTestBase {

  @Test
  @DisplayName("AC-001-01 a valid external registration with active options is accepted")
  void ac001_01_validExternalRegistrationIsAccepted() {
    ObjectNode registration = Registrations.external();
    ApiClient.Response response = api.register(registration);

    assertThat(response.status()).as(response.text()).isEqualTo(201);
    JsonNode body = response.json();
    assertThat(body.get("type").asString()).isEqualTo("EXTERNAL");
    UUID id = idOf(body);
    Map<String, Object> row =
        database.registrationByEmail(Registrations.email(registration)).orElseThrow();
    assertThat(row.get("id")).isEqualTo(id);
    assertThat(row.get("type")).isEqualTo("EXTERNAL");
    assertThat(database.options(id))
        .extracting(o -> o.get("option_id"))
        .containsExactly("ws-ai", "meal-lunch-day1");
  }

  @Test
  @DisplayName("AC-001-02 the external registration takes exactly the external fields")
  void ac001_02_externalRegistrationTakesOnlyExternalFields() {
    ObjectNode withStudentField = Registrations.external();
    withStudentField.put("studentId", "63210001");
    ApiClient.Response response = api.register(withStudentField);

    assertFieldError(response, "studentId", "NOT_ALLOWED");
    assertNothingStoredFor(Registrations.email(withStudentField));
  }

  @ParameterizedTest(name = "AC-001-03 required field {0} empty, blank or missing is rejected")
  @ValueSource(strings = {"firstName", "lastName", "email", "organization"})
  void ac001_03_missingRequiredFieldIsRejected(String field) {
    for (String variant : new String[] {"", "   \t ", null}) {
      ObjectNode registration = Registrations.external();
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
  @DisplayName("AC-001-04 surrounding whitespace is not stored")
  void ac001_04_surroundingWhitespaceIsTrimmed() {
    ObjectNode registration = Registrations.external();
    String email = Registrations.email(registration);
    registration.put("firstName", "  Ana ");
    registration.put("lastName", "\tNovak  ");
    registration.put("email", "  " + email + " ");
    registration.put("organization", " Univerza v Mariboru\t");

    registerAccepted(registration);

    Map<String, Object> row = database.registrationByEmail(email).orElseThrow();
    assertThat(row.get("first_name")).isEqualTo("Ana");
    assertThat(row.get("last_name")).isEqualTo("Novak");
    assertThat(row.get("email")).isEqualTo(email);
    assertThat(row.get("organization")).isEqualTo("Univerza v Mariboru");
  }

  @ParameterizedTest(name = "AC-001-05 invalid email \"{0}\" is rejected")
  @ValueSource(
      strings = {
        "ana.example.si",
        "ana@",
        "@example.si",
        "ana@example",
        "ana novak@example.si",
        "ana@@example.si"
      })
  void ac001_05_invalidEmailIsRejected(String invalid) {
    ObjectNode registration = Registrations.external();
    registration.put("email", invalid);
    ApiClient.Response response = api.register(registration);

    assertFieldError(response, "email", "INVALID_EMAIL");
    assertThat(database.countByEmail(invalid)).isZero();
  }

  @Test
  @DisplayName("AC-001-06 Slovenian and other Unicode letters are stored unchanged")
  void ac001_06_unicodeIsStoredUnchanged() {
    ObjectNode registration = Registrations.external();
    registration.put("firstName", "Špela Ćiril");
    registration.put("lastName", "Žagar-Čebašek Øster");
    registration.put("organization", "Inštitut Jožef Stefan – Odsek za računalništvo (ščž ŠČŽ)");

    registerAccepted(registration);

    Map<String, Object> row =
        database.registrationByEmail(Registrations.email(registration)).orElseThrow();
    assertThat(row.get("first_name")).isEqualTo("Špela Ćiril");
    assertThat(row.get("last_name")).isEqualTo("Žagar-Čebašek Øster");
    assertThat(row.get("organization"))
        .isEqualTo("Inštitut Jožef Stefan – Odsek za računalništvo (ščž ŠČŽ)");
  }

  @Test
  @DisplayName("AC-001-07 an option identifier that is not configured is rejected")
  void ac001_07_unknownOptionIsRejected() {
    ObjectNode registration =
        Registrations.withOptions(Registrations.external(), "ws-ai", "no-such-option");
    ApiClient.Response response = api.register(registration);

    assertFieldError(response, "optionIds", "UNKNOWN_OPTION");
    assertNothingStoredFor(Registrations.email(registration));
  }

  @Test
  @DisplayName("AC-001-08 an inactive option is rejected")
  void ac001_08_inactiveOptionIsRejected() {
    ObjectNode registration = Registrations.withOptions(Registrations.external(), "ws-legacy");
    ApiClient.Response response = api.register(registration);

    assertFieldError(response, "optionIds", "INACTIVE_OPTION");
    assertNothingStoredFor(Registrations.email(registration));
  }

  @Test
  @DisplayName("AC-001-09 a registration without any option is accepted")
  void ac001_09_noOptionIsAccepted() {
    ObjectNode registration = Registrations.withOptions(Registrations.external());

    UUID id = idOf(registerAccepted(registration));

    assertThat(database.countByEmail(Registrations.email(registration))).isEqualTo(1);
    assertThat(database.options(id)).isEmpty();
  }

  @Test
  @DisplayName("AC-001-10 the same option selected twice is rejected")
  void ac001_10_duplicateOptionIsRejected() {
    ObjectNode registration =
        Registrations.withOptions(Registrations.external(), "ws-ai", "ev-reception", "ws-ai");
    ApiClient.Response response = api.register(registration);

    assertFieldError(response, "optionIds", "DUPLICATE_OPTION");
    assertNothingStoredFor(Registrations.email(registration));
  }

  @Test
  @DisplayName("AC-001-11 the form gets the configured consent wording")
  void ac001_11_consentWordingComesFromConfiguration() {
    ApiClient.Response response = api.formConfig();

    assertThat(response.status()).isEqualTo(200);
    JsonNode consent = response.json().get("consent");
    assertThat(consent.get("id").asString()).isEqualTo("personal-data-v1");
    assertThat(consent.get("text").asString())
        .isEqualTo(
            "I agree that the organizers process my personal data given in this form to organise"
                + " my attendance at the conference and the activities I selected.");
  }

  @Test
  @DisplayName("AC-001-12 a registration without the mandatory consent is rejected")
  void ac001_12_missingConsentIsRejected() {
    ObjectNode notGiven = Registrations.external();
    notGiven.put("consentGiven", false);
    assertFieldError(api.register(notGiven), "consentGiven", "CONSENT_REQUIRED");
    assertNothingStoredFor(Registrations.email(notGiven));

    ObjectNode absent = Registrations.external();
    absent.remove("consentGiven");
    assertFieldError(api.register(absent), "consentGiven", "CONSENT_REQUIRED");
    assertNothingStoredFor(Registrations.email(absent));
  }

  @Test
  @DisplayName("AC-001-13 a failed or missing anti-automation check is rejected")
  void ac001_13_failedOrMissingCaptchaIsRejected() {
    ObjectNode failed = Registrations.external();
    failed.put("captchaToken", "not-a-valid-token");
    assertFieldError(api.register(failed), "captchaToken", "CAPTCHA_FAILED");
    assertNothingStoredFor(Registrations.email(failed));

    ObjectNode missing = Registrations.external();
    missing.remove("captchaToken");
    assertFieldError(api.register(missing), "captchaToken", "REQUIRED");
    assertNothingStoredFor(Registrations.email(missing));
  }

  @Test
  @DisplayName("AC-001-14 a second registration with an already registered email is rejected")
  void ac001_14_duplicateEmailIsRejected() {
    ObjectNode first = Registrations.student();
    String email = Registrations.email(first);
    registerAccepted(first);

    ObjectNode second = Registrations.external();
    second.put("email", "  " + email.toUpperCase(java.util.Locale.ROOT) + " ");
    ApiClient.Response response = api.register(second);

    assertThat(response.status()).as(response.text()).isEqualTo(409);
    JsonNode problem = response.json();
    assertThat(problem.get("code").asString()).isEqualTo("EMAIL_ALREADY_REGISTERED");
    assertThat(problem.get("detail").asString()).containsIgnoringCase("already registered");
    assertThat(problem.get("detail").asString()).containsIgnoringCase("organizers");
    assertThat(database.countByEmail(email)).isEqualTo(1);
    assertThat(database.registrationByEmail(email).orElseThrow().get("type")).isEqualTo("STUDENT");
  }
}

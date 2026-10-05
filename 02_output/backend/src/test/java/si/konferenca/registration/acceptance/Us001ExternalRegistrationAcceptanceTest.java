package si.konferenca.registration.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static si.konferenca.registration.acceptance.support.Problems.assertFieldError;
import static si.konferenca.registration.acceptance.support.Registrations.CONSENT_DATA;
import static si.konferenca.registration.acceptance.support.Registrations.CONSENT_PHOTO;
import static si.konferenca.registration.acceptance.support.Registrations.OPTION_EXTERNAL_ONLY;
import static si.konferenca.registration.acceptance.support.Registrations.OPTION_INACTIVE;
import static si.konferenca.registration.acceptance.support.Registrations.OPTION_MEAL;
import static si.konferenca.registration.acceptance.support.Registrations.OPTION_WORKSHOP;
import static si.konferenca.registration.acceptance.support.Registrations.external;
import static si.konferenca.registration.acceptance.support.Registrations.withConsents;
import static si.konferenca.registration.acceptance.support.Registrations.withOptions;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import si.konferenca.registration.acceptance.support.AcceptanceTestBase;
import si.konferenca.registration.acceptance.support.Api;
import si.konferenca.registration.acceptance.support.Database;
import si.konferenca.registration.acceptance.support.JsonCopies;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/** US-001 External participant registration, through the REST API. */
class Us001ExternalRegistrationAcceptanceTest extends AcceptanceTestBase {

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    configure(registry, Map.of());
  }

  @Test
  void ac001_01_externalFormAcceptsOnlyTheExternalFields() {
    ObjectNode request = external();
    request.put("studentId", "63210001");

    Api.Response response = api.register(request);

    assertFieldError(response, 400, "studentId", "NOT_ALLOWED");
    assertNothingStored();
  }

  @Test
  void ac001_02_validExternalRegistrationIsAcceptedWithSelectedOptions() {
    Api.Response response =
        api.register(withOptions(external(), OPTION_WORKSHOP, OPTION_EXTERNAL_ONLY, OPTION_MEAL));

    assertThat(response.status()).as("body: %s", response.text()).isEqualTo(201);
    JsonNode body = response.json();
    assertThat(body.path("type").asString()).isEqualTo("external");
    assertThat(body.path("options").findValuesAsString("id"))
        .containsExactlyInAnyOrder(OPTION_WORKSHOP, OPTION_EXTERNAL_ONLY, OPTION_MEAL);
    List<Map<String, Object>> rows =
        Database.rows(
            "SELECT type, organization FROM registration WHERE id = ?::uuid",
            body.path("registrationId").asString());
    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).get("type")).isEqualTo("external");
  }

  @ParameterizedTest
  @ValueSource(strings = {"firstName", "lastName", "email", "organization"})
  void ac001_03_emptyRequiredFieldIsRejectedAtThatField(String field) {
    ObjectNode request = external();
    request.put(field, "");

    assertFieldError(api.register(request), 400, field, "REQUIRED");
    assertNothingStored();
  }

  @ParameterizedTest
  @ValueSource(strings = {"firstName", "lastName", "email", "organization"})
  void ac001_03_missingRequiredFieldIsRejectedAtThatField(String field) {
    ObjectNode request = external();
    request.remove(field);

    assertFieldError(api.register(request), 400, field, "REQUIRED");
    assertNothingStored();
  }

  @ParameterizedTest
  @ValueSource(strings = {"firstName", "lastName", "organization"})
  void ac001_04_whitespaceOnlyRequiredFieldIsRejectedAsEmpty(String field) {
    ObjectNode request = external();
    request.put(field, "  \t ");

    assertFieldError(api.register(request), 400, field, "REQUIRED");
    assertNothingStored();
  }

  @Test
  void ac001_05_leadingAndTrailingWhitespaceIsRemovedBeforeStoring() {
    ObjectNode request = external();
    request.put("firstName", "  Ana ");
    request.put("lastName", "\tNovak  ");
    request.put("email", " ana.novak@example.com ");
    request.put("organization", "  Institut Jozef Stefan\t");

    Api.Response response = api.register(request);

    assertThat(response.status()).as("body: %s", response.text()).isEqualTo(201);
    String id = response.json().path("registrationId").asString();
    Map<String, Object> row =
        Database.rows(
                "SELECT first_name, last_name, email, organization FROM registration"
                    + " WHERE id = ?::uuid",
                id)
            .get(0);
    assertThat(row)
        .containsEntry("first_name", "Ana")
        .containsEntry("last_name", "Novak")
        .containsEntry("email", "ana.novak@example.com")
        .containsEntry("organization", "Institut Jozef Stefan");
    JsonNode participant = JsonCopies.read(id).path("participant");
    assertThat(participant.path("firstName").asString()).isEqualTo("Ana");
    assertThat(participant.path("organization").asString()).isEqualTo("Institut Jozef Stefan");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"ana", "ana@", "@example.com", "ana@example", "ana novak@example.com", "a@b@c.si"})
  void ac001_06_invalidEmailIsRejectedAtTheEmailField(String email) {
    ObjectNode request = external();
    request.put("email", email);

    assertFieldError(api.register(request), 400, "email", "INVALID_EMAIL");
    assertNothingStored();
  }

  @Test
  void ac001_07_slovenianCharactersAreStoredUnchanged() {
    ObjectNode request = external();
    request.put("firstName", "Žiga");
    request.put("lastName", "Čebašek-Šuštar");
    request.put("organization", "Fakulteta za računalništvo in informatiko, Ljubljana");

    Api.Response response = api.register(request);

    assertThat(response.status()).as("body: %s", response.text()).isEqualTo(201);
    String id = response.json().path("registrationId").asString();
    Map<String, Object> row =
        Database.rows(
                "SELECT first_name, last_name, organization FROM registration WHERE id = ?::uuid",
                id)
            .get(0);
    assertThat(row)
        .containsEntry("first_name", "Žiga")
        .containsEntry("last_name", "Čebašek-Šuštar")
        .containsEntry("organization", "Fakulteta za računalništvo in informatiko, Ljubljana");
    JsonNode participant = JsonCopies.read(id).path("participant");
    assertThat(participant.path("lastName").asString()).isEqualTo("Čebašek-Šuštar");
    assertThat(response.json().path("firstName").asString()).isEqualTo("Žiga");
  }

  @Test
  void ac001_08_unknownOptionIsRejected() {
    assertFieldError(
        api.register(withOptions(external(), OPTION_WORKSHOP, "ws-does-not-exist")),
        400,
        "optionIds",
        "UNKNOWN_OPTION");
    assertNothingStored();
  }

  @Test
  void ac001_09_inactiveOptionIsRejected() {
    assertFieldError(
        api.register(withOptions(external(), OPTION_INACTIVE)),
        400,
        "optionIds",
        "INACTIVE_OPTION");
    assertNothingStored();
  }

  @Test
  void ac001_10_sameOptionTwiceIsRejected() {
    assertFieldError(
        api.register(withOptions(external(), OPTION_WORKSHOP, OPTION_WORKSHOP)),
        400,
        "optionIds",
        "DUPLICATE_OPTION");
    assertNothingStored();
  }

  @Test
  void ac001_11_registrationWithoutOptionsIsAccepted() {
    Api.Response response = api.register(withOptions(external()));

    assertThat(response.status()).as("body: %s", response.text()).isEqualTo(201);
    assertThat(response.json().path("options").isEmpty()).isTrue();
    assertThat(Database.registrationCount()).isEqualTo(1);
  }

  @Test
  void ac001_13_missingMandatoryConsentIsRejectedAtThatConsent() {
    assertFieldError(
        api.register(withConsents(external())),
        400,
        "consents." + CONSENT_DATA,
        "CONSENT_REQUIRED");
    assertNothingStored();
  }

  @Test
  void ac001_13_optionalConsentAloneDoesNotReplaceTheMandatoryOne() {
    assertFieldError(
        api.register(withConsents(external(), CONSENT_PHOTO)),
        400,
        "consents." + CONSENT_DATA,
        "CONSENT_REQUIRED");
    assertNothingStored();
  }

  @Test
  void ac001_14_rejectedAntiAutomationTokenIsRefusedAndNothingStored() {
    ObjectNode request = external();
    request.put("captchaToken", "not-the-test-token");

    assertFieldError(api.register(request), 400, "captchaToken", "CAPTCHA_FAILED");
    assertNothingStored();
  }

  @Test
  void ac001_14_missingAntiAutomationTokenIsRefusedAndNothingStored() {
    ObjectNode request = external();
    request.remove("captchaToken");

    Api.Response response = api.register(request);

    assertThat(response.status()).isEqualTo(400);
    assertThat(response.json().path("errors").findValuesAsString("field")).contains("captchaToken");
    assertNothingStored();
  }

  @Test
  void ac001_15_secondRegistrationWithSameEmailIsRejected() {
    assertThat(api.register(external()).status()).isEqualTo(201);
    ObjectNode second = external();
    second.put("firstName", "Anja");
    second.put("email", "  ANA.Novak@Example.COM ");

    Api.Response response = api.register(second);

    assertFieldError(response, 409, "email", "DUPLICATE_EMAIL");
    assertThat(response.text().toLowerCase(java.util.Locale.ROOT)).contains("organizer");
    assertThat(Database.registrationCount()).isEqualTo(1);
    assertThat(JsonCopies.files()).hasSize(1);
  }
}

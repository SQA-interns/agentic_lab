package si.konferenca.registration.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static si.konferenca.registration.acceptance.support.Api.array;
import static si.konferenca.registration.acceptance.support.Api.assertFieldError;
import static si.konferenca.registration.acceptance.support.Api.assertNothingStoredFor;
import static si.konferenca.registration.acceptance.support.Api.cell;
import static si.konferenca.registration.acceptance.support.Api.exportRowsFor;
import static si.konferenca.registration.acceptance.support.Api.external;
import static si.konferenca.registration.acceptance.support.Api.jsonCopies;
import static si.konferenca.registration.acceptance.support.Api.register;
import static si.konferenca.registration.acceptance.support.Api.uniqueEmail;
import static si.konferenca.registration.acceptance.support.Api.workbookRows;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import si.konferenca.registration.acceptance.support.Api;
import si.konferenca.registration.acceptance.support.Api.Response;
import si.konferenca.registration.acceptance.support.Backend;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/** US-001 External participant registration, through the REST API. */
class Us001ExternalRegistrationAcceptanceTest {

  private static Backend backend;

  @BeforeAll
  static void start() {
    backend = Backend.startDefault();
  }

  @AfterAll
  static void stop() {
    backend.close();
  }

  private static List<String> names(JsonNode array, String property) {
    List<String> values = new ArrayList<>();
    array.forEach(n -> values.add(n.get(property).asString()));
    return values;
  }

  @Test
  void AC_001_01_externalFormAsksForExactlyTheExternalFields() {
    Response response = Api.get(backend.url("/api/registration-form/external"));

    assertThat(response.status()).isEqualTo(200);
    JsonNode form = response.json();
    assertThat(form.get("type").asString()).isEqualTo("EXTERNAL");
    assertThat(names(form.get("fields"), "name"))
        .containsExactly("firstName", "lastName", "email", "organization");
    assertThat(form.get("categories").size()).isEqualTo(4);
    assertThat(names(form.get("consents"), "id")).containsExactly("data-processing", "photo");
  }

  @Test
  void AC_001_02_validExternalRegistrationWithOptionsIsAccepted() {
    String email = uniqueEmail();
    ObjectNode body = external(email);
    body.set("optionIds", array("ws-industry-masterclass", "ev-gala-dinner", "other-city-tour"));

    Response response = register(backend, body);

    assertThat(response.status()).as(response.text()).isEqualTo(201);
    JsonNode accepted = response.json();
    assertThat(accepted.get("id").asString()).matches("[0-9a-f-]{36}");
    assertThat(accepted.get("type").asString()).isEqualTo("EXTERNAL");
    assertThat(names(accepted.get("selectedOptions"), "id"))
        .containsExactlyInAnyOrder("ws-industry-masterclass", "ev-gala-dinner", "other-city-tour");
    assertThat(exportRowsFor(backend, email)).hasSize(1);
  }

  @Test
  void AC_001_03_externalRegistrationWithoutOptionsIsAccepted() {
    String email = uniqueEmail();
    ObjectNode body = external(email);
    body.set("optionIds", array());

    Response response = register(backend, body);

    assertThat(response.status()).as(response.text()).isEqualTo(201);
    assertThat(response.json().get("selectedOptions").size()).isZero();
    assertThat(exportRowsFor(backend, email)).hasSize(1);
  }

  @ParameterizedTest
  @ValueSource(strings = {"firstName", "lastName", "email", "organization"})
  void AC_001_04_emptyRequiredFieldIsRejected(String field) {
    String email = uniqueEmail();
    ObjectNode body = external(email);
    body.put(field, "");
    int copies = jsonCopies(backend).size();

    Response response = register(backend, body);

    assertFieldError(response, 400, field, "REQUIRED");
    assertNothingStoredFor(backend, email, copies);
  }

  @ParameterizedTest
  @ValueSource(strings = {"firstName", "lastName", "organization"})
  void AC_001_05_whitespaceOnlyRequiredFieldIncludingNbspIsRejected(String field) {
    String email = uniqueEmail();
    ObjectNode body = external(email);
    body.put(field, "  \t  ");
    int copies = jsonCopies(backend).size();

    Response response = register(backend, body);

    assertFieldError(response, 400, field, "REQUIRED");
    assertNothingStoredFor(backend, email, copies);
  }

  @Test
  void AC_001_05_whitespaceOnlyEmailIncludingNbspIsRejected() {
    ObjectNode body = external("   ");

    Response response = register(backend, body);

    assertFieldError(response, 400, "email", "REQUIRED");
  }

  @Test
  void AC_001_06_surroundingWhitespaceIncludingNbspIsRemovedFromStoredValues() {
    String email = uniqueEmail();
    ObjectNode body = external("  " + email + "  ");
    body.put("firstName", "  Ana  ");
    body.put("lastName", "  Novak\t");
    body.put("organization", " Institut Jožef Stefan ");

    Response response = register(backend, body);

    assertThat(response.status()).as(response.text()).isEqualTo(201);
    List<List<String>> rows = workbookRows(Api.export(backend));
    List<String> row = exportRowsFor(backend, email).getFirst();
    assertThat(cell(rows, row, "First name")).isEqualTo("Ana");
    assertThat(cell(rows, row, "Last name")).isEqualTo("Novak");
    assertThat(cell(rows, row, "Email")).isEqualTo(email);
    assertThat(cell(rows, row, "Organization / institution")).isEqualTo("Institut Jožef Stefan");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"ana.novak", "ana@", "@example.si", "ana novak@example.si", "ana@example"})
  void AC_001_07_invalidEmailFormatIsRejected(String invalid) {
    ObjectNode body = external(invalid);
    int copies = jsonCopies(backend).size();

    Response response = register(backend, body);

    assertFieldError(response, 400, "email", "INVALID_EMAIL");
    assertThat(jsonCopies(backend)).hasSize(copies);
  }

  @Test
  void AC_001_08_slovenianCharactersAreAcceptedAndKeptUnchanged() {
    String email = uniqueEmail();
    ObjectNode body = external(email);
    body.put("firstName", "Čedomir Žiga");
    body.put("lastName", "Šušteršič");
    body.put("organization", "Fakulteta za računalništvo, ČŠŽ čšž");

    Response response = register(backend, body);

    assertThat(response.status()).as(response.text()).isEqualTo(201);
    List<List<String>> rows = workbookRows(Api.export(backend));
    List<String> row = exportRowsFor(backend, email).getFirst();
    assertThat(cell(rows, row, "First name")).isEqualTo("Čedomir Žiga");
    assertThat(cell(rows, row, "Last name")).isEqualTo("Šušteršič");
    assertThat(cell(rows, row, "Organization / institution"))
        .isEqualTo("Fakulteta za računalništvo, ČŠŽ čšž");
  }

  @Test
  void AC_001_09_onlyActiveOptionsForExternalParticipantsAreOfferedGroupedByCategory() {
    JsonNode form = Api.form(backend, "external");

    JsonNode categories = form.get("categories");
    assertThat(names(categories, "category")).containsExactly("WORKSHOP", "EVENT", "MEAL", "OTHER");
    assertThat(names(categories.get(0).get("options"), "id"))
        .containsExactlyInAnyOrder("ws-testing-ai", "ws-industry-masterclass");
    assertThat(names(categories.get(1).get("options"), "id"))
        .containsExactlyInAnyOrder("ev-gala-dinner", "ev-reception");
    assertThat(names(categories.get(2).get("options"), "id")).containsExactly("meal-lunch-day1");
    assertThat(names(categories.get(3).get("options"), "id")).containsExactly("other-city-tour");
  }

  @Test
  void AC_001_10_unknownOptionIsRejected() {
    String email = uniqueEmail();
    ObjectNode body = external(email);
    body.set("optionIds", array("ws-testing-ai", "ws-does-not-exist"));
    int copies = jsonCopies(backend).size();

    Response response = register(backend, body);

    assertFieldError(response, 400, "optionIds", "OPTION_NOT_AVAILABLE");
    assertNothingStoredFor(backend, email, copies);
  }

  @Test
  void AC_001_11_inactiveOptionIsRejected() {
    String email = uniqueEmail();
    ObjectNode body = external(email);
    body.set("optionIds", array("ws-retired"));
    int copies = jsonCopies(backend).size();

    Response response = register(backend, body);

    assertFieldError(response, 400, "optionIds", "OPTION_NOT_AVAILABLE");
    assertNothingStoredFor(backend, email, copies);
  }

  @Test
  void AC_001_12_moreOptionsInACategoryThanTheMaximumIsRejected() {
    String email = uniqueEmail();
    ObjectNode body = external(email);
    body.set("optionIds", array("ev-gala-dinner", "ev-reception"));
    int copies = jsonCopies(backend).size();

    Response response = register(backend, body);

    assertFieldError(response, 400, "optionIds", "TOO_MANY_OPTIONS");
    assertNothingStoredFor(backend, email, copies);
  }

  @Test
  void AC_001_13_formDefinitionDoesNotPreselectAnyConsent() {
    JsonNode form = Api.form(backend, "external");

    assertThat(form.get("consents").size()).isPositive();
    form.get("consents")
        .forEach(
            consent -> {
              assertThat(consent.has("given")).isFalse();
              assertThat(consent.has("checked")).isFalse();
              assertThat(consent.has("preselected")).isFalse();
            });
  }

  @Test
  void AC_001_14_missingMandatoryConsentIsRejectedAndIdentified() {
    String email = uniqueEmail();
    ObjectNode body = external(email);
    body.set("consentIds", array("photo"));
    int copies = jsonCopies(backend).size();

    Response response = register(backend, body);

    assertFieldError(response, 400, "consentIds", "CONSENT_REQUIRED");
    List<String> missing = new ArrayList<>();
    response
        .json()
        .get("errors")
        .forEach(
            e -> {
              if ("CONSENT_REQUIRED".equals(e.get("code").asString())) {
                missing.add(e.get("consentId").asString());
              }
            });
    assertThat(missing).containsExactly("data-processing");
    assertNothingStoredFor(backend, email, copies);
  }

  @Test
  void AC_001_15_secondRegistrationWithTheSameEmailIsRejected() {
    String email = uniqueEmail();
    assertThat(register(backend, external(email)).status()).isEqualTo(201);
    int copies = jsonCopies(backend).size();

    Response response = register(backend, external("  " + email.toUpperCase() + " "));

    assertFieldError(response, 409, "email", "ALREADY_REGISTERED");
    assertThat(exportRowsFor(backend, email)).hasSize(1);
    assertThat(jsonCopies(backend)).hasSize(copies);
  }

  @Test
  void AC_001_16_failedAntiAutomationCheckIsRejected() {
    String email = uniqueEmail();
    ObjectNode body = external(email);
    body.put("recaptchaToken", "not-a-valid-token");
    int copies = jsonCopies(backend).size();

    Response response = register(backend, body);

    assertFieldError(response, 400, "recaptchaToken", "CAPTCHA_FAILED");
    assertNothingStoredFor(backend, email, copies);
  }

  @Test
  void AC_001_16_missingAntiAutomationTokenIsRejected() {
    String email = uniqueEmail();
    ObjectNode body = external(email);
    body.remove("recaptchaToken");
    int copies = jsonCopies(backend).size();

    Response response = register(backend, body);

    assertThat(response.status()).isEqualTo(400);
    assertThat(response.text()).contains("recaptchaToken");
    assertNothingStoredFor(backend, email, copies);
  }
}

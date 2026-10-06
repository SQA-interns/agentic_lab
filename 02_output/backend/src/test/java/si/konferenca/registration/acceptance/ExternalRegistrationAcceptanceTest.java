package si.konferenca.registration.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static si.konferenca.registration.acceptance.support.Registrations.external;
import static si.konferenca.registration.acceptance.support.Registrations.uniqueEmail;
import static si.konferenca.registration.acceptance.support.Registrations.with;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import si.konferenca.registration.acceptance.support.RunningApp;
import si.konferenca.registration.acceptance.support.RunningApp.Response;
import tools.jackson.databind.JsonNode;

/** US-001 External participant registration, through the REST API (openapi.yaml). */
class ExternalRegistrationAcceptanceTest {

  private static RunningApp app;

  @BeforeAll
  static void start() {
    app = RunningApp.start();
  }

  @AfterAll
  static void stop() {
    app.close();
  }

  private static void assertRejectedAndNothingStored(Response r, String email) {
    assertThat(r.status()).isEqualTo(400);
    assertThat(r.header("Content-Type")).startsWith("application/problem+json");
    assertThat(app.registrationRows(email)).isEmpty();
  }

  @Test
  void AC_001_01_validExternalRegistrationIsAccepted() {
    Map<String, Object> reg = external();

    Response r = app.register(reg);

    assertThat(r.status()).isEqualTo(201);
    JsonNode body = r.json();
    assertThat(body.path("registrationId").asString()).matches("[0-9a-f-]{36}");
    assertThat(body.path("receivedAt").asString()).isNotBlank();
    List<Map<String, Object>> rows = app.registrationRows((String) reg.get("email"));
    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).get("registration_type")).isEqualTo("EXTERNAL");
  }

  @ParameterizedTest
  @ValueSource(strings = {"firstName", "lastName", "email", "organization"})
  void AC_001_02_emptyRequiredFieldIsRejectedAndIdentified(String field) {
    Map<String, Object> reg = external();
    String email = (String) reg.get("email");

    Response r = app.register(with(reg, field, ""));

    assertRejectedAndNothingStored(r, email);
    assertThat(r.fieldErrors()).containsEntry(field, "REQUIRED");
  }

  @ParameterizedTest
  @ValueSource(strings = {"firstName", "lastName", "email", "organization"})
  void AC_001_02_missingRequiredFieldIsRejectedAndIdentified(String field) {
    Map<String, Object> reg = external();
    String email = (String) reg.get("email");

    Response r = app.register(with(reg, field, null));

    assertThat(r.status()).isEqualTo(400);
    assertThat(app.registrationRows(email)).isEmpty();
    assertThat(r.fieldErrors()).containsEntry(field, "REQUIRED");
  }

  @ParameterizedTest
  @ValueSource(strings = {"   ", "\t\n", " ", "     　 "})
  void AC_001_03_whitespaceOnlyFieldIncludingNoBreakSpaceIsRejected(String blank) {
    Map<String, Object> reg = external();
    String email = (String) reg.get("email");

    Response r = app.register(with(reg, "organization", blank));

    assertRejectedAndNothingStored(r, email);
    assertThat(r.fieldErrors()).containsEntry("organization", "REQUIRED");
  }

  @Test
  void AC_001_03_whitespaceOnlyFirstNameIsRejected() {
    Map<String, Object> reg = external();

    Response r = app.register(with(reg, "firstName", "  "));

    assertRejectedAndNothingStored(r, (String) reg.get("email"));
    assertThat(r.fieldErrors()).containsEntry("firstName", "REQUIRED");
  }

  @Test
  void AC_001_04_leadingAndTrailingWhitespaceIsNotStored() {
    String email = uniqueEmail("trim");
    Map<String, Object> reg = external();
    reg.put("firstName", "  Ana\t");
    reg.put("lastName", "  Novak ");
    reg.put("email", "  " + email + "  ");
    reg.put("organization", " Institut Jožef Stefan  ");

    Response r = app.register(reg);

    assertThat(r.status()).isEqualTo(201);
    Map<String, Object> row = app.registrationRows(email).get(0);
    assertThat(row.get("first_name")).isEqualTo("Ana");
    assertThat(row.get("last_name")).isEqualTo("Novak");
    assertThat(row.get("email")).isEqualTo(email);
    assertThat(row.get("organization")).isEqualTo("Institut Jožef Stefan");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"ana", "ana@", "@example.si", "ana@example", "ana example@x.si", "a@b@c.si"})
  void AC_001_05_invalidEmailIsRejected(String invalid) {
    Map<String, Object> reg = external();

    Response r = app.register(with(reg, "email", invalid));

    assertThat(r.status()).isEqualTo(400);
    assertThat(r.fieldErrors()).containsEntry("email", "INVALID_EMAIL");
    assertThat(app.registrationRows(invalid)).isEmpty();
  }

  @Test
  void AC_001_06_slovenianCharactersAreAcceptedAndStoredUnchanged() {
    Map<String, Object> reg = external();
    reg.put("firstName", "Čeh Šárka Žiga");
    reg.put("lastName", "ČŠŽ čšž Ćosić Đurić");
    reg.put("organization", "Fakulteta za računalništvo in informatiko, Ljubljana – Šiška");

    Response r = app.register(reg);

    assertThat(r.status()).isEqualTo(201);
    Map<String, Object> row = app.registrationRows((String) reg.get("email")).get(0);
    assertThat(row.get("first_name")).isEqualTo("Čeh Šárka Žiga");
    assertThat(row.get("last_name")).isEqualTo("ČŠŽ čšž Ćosić Đurić");
    assertThat(row.get("organization"))
        .isEqualTo("Fakulteta za računalništvo in informatiko, Ljubljana – Šiška");
  }

  @Test
  void AC_001_07_inactiveOptionIsRejectedAndNothingStored() {
    Map<String, Object> reg = with(external(), "optionIds", List.of("ws-ai", "meal-breakfast"));

    Response r = app.register(reg);

    assertRejectedAndNothingStored(r, (String) reg.get("email"));
    assertThat(r.fieldErrors()).containsValue("INACTIVE_OPTION");
  }

  @Test
  void AC_001_08_unknownOptionIsRejectedAndNothingStored() {
    Map<String, Object> reg = with(external(), "optionIds", List.of("no-such-option"));

    Response r = app.register(reg);

    assertRejectedAndNothingStored(r, (String) reg.get("email"));
    assertThat(r.fieldErrors()).containsValue("UNKNOWN_OPTION");
  }

  @Test
  void AC_001_09_missingConsentIsRejectedAndIdentified() {
    Map<String, Object> reg = with(external(), "consentGiven", false);

    Response r = app.register(reg);

    assertRejectedAndNothingStored(r, (String) reg.get("email"));
    assertThat(r.fieldErrors()).containsEntry("consentGiven", "CONSENT_REQUIRED");
  }

  @Test
  void AC_001_10_consentWordingIsOfferedToTheForm() {
    Response r = app.get("/api/options");

    assertThat(r.status()).isEqualTo(200);
    JsonNode consent = r.json().path("consent");
    assertThat(consent.path("id").asString()).isEqualTo("data-processing");
    assertThat(consent.path("text").asString())
        .isEqualTo("I agree to the processing of my personal data for organising the conference.");
  }

  @Test
  void AC_001_11_onlyActiveOptionsAreOfferedWithCategoryAndName() {
    Response r = app.get("/api/options");

    assertThat(r.status()).isEqualTo(200);
    JsonNode options = r.json().path("options");
    List<String> ids = options.valueStream().map(o -> o.path("id").asString()).toList();
    assertThat(ids)
        .containsExactlyInAnyOrder(
            "ws-ai", "ws-security", "ev-gala", "ev-tour", "meal-lunch", "other-poster");
    JsonNode ai =
        options
            .valueStream()
            .filter(o -> o.path("id").asString().equals("ws-ai"))
            .findFirst()
            .get();
    assertThat(ai.path("name").asString()).isEqualTo("AI workshop");
    assertThat(ai.path("category").asString()).isEqualTo("WORKSHOP");
    assertThat(options.valueStream().map(o -> o.path("category").asString()).distinct().toList())
        .containsExactlyInAnyOrder("WORKSHOP", "EVENT", "MEAL", "OTHER");
  }

  @Test
  void AC_001_12_registrationWithoutOptionsIsAccepted() {
    Map<String, Object> reg = with(external(), "optionIds", List.of());

    Response r = app.register(reg);

    assertThat(r.status()).isEqualTo(201);
    Map<String, Object> row = app.registrationRows((String) reg.get("email")).get(0);
    assertThat(app.optionRows(row.get("id"))).isEmpty();
  }

  @Test
  void AC_001_13_severalOptionsOfOneCategoryAreAccepted() {
    Map<String, Object> reg =
        with(external(), "optionIds", List.of("ws-ai", "ws-security", "ev-gala", "ev-tour"));

    Response r = app.register(reg);

    assertThat(r.status()).isEqualTo(201);
    Map<String, Object> row = app.registrationRows((String) reg.get("email")).get(0);
    assertThat(app.optionRows(row.get("id")).stream().map(o -> o.get("option_id")).toList())
        .containsExactlyInAnyOrder("ws-ai", "ws-security", "ev-gala", "ev-tour");
  }

  @Test
  void AC_001_14_failedAntiAutomationCheckIsRejectedAndNothingStored() {
    Map<String, Object> reg = with(external(), "recaptchaToken", "test-fail");

    Response r = app.register(reg);

    assertRejectedAndNothingStored(r, (String) reg.get("email"));
    assertThat(r.fieldErrors()).containsEntry("recaptchaToken", "RECAPTCHA_FAILED");
  }

  @Test
  void AC_001_14_missingAntiAutomationTokenIsRejectedAndNothingStored() {
    Map<String, Object> reg = with(external(), "recaptchaToken", null);

    Response r = app.register(reg);

    assertThat(r.status()).isEqualTo(400);
    assertThat(app.registrationRows((String) reg.get("email"))).isEmpty();
  }

  @Test
  void AC_001_15_secondRegistrationWithSameEmailIsRejected() {
    Map<String, Object> first = external();
    String email = (String) first.get("email");
    assertThat(app.register(first).status()).isEqualTo(201);
    Map<String, Object> second = external();
    second.put("email", "  " + email.toUpperCase() + " ");
    second.put("firstName", "Druga");

    Response r = app.register(second);

    assertThat(r.status()).isEqualTo(409);
    assertThat(r.json().path("code").asString()).isEqualTo("DUPLICATE_EMAIL");
    assertThat(app.registrationRows(email)).hasSize(1);
    assertThat(app.registrationRows(email).get(0).get("first_name")).isEqualTo("Ana");
  }
}

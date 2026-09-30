package lab.conference.acceptance;

import static lab.conference.acceptance.support.Checks.assertNothingStored;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import lab.conference.acceptance.support.Api;
import lab.conference.acceptance.support.AppInstance;
import lab.conference.acceptance.support.Payloads;
import lab.conference.acceptance.support.Store;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/** US-001: external participant registration through the REST API. */
class ExternalRegistrationAcceptanceTest {

  private static AppInstance app;
  private static Api api;

  @BeforeAll
  static void start() {
    app = AppInstance.builder().build().start();
    api = app.api();
  }

  @AfterAll
  static void stop() {
    app.stop();
  }

  @Test
  void ac_001_01_validExternalSubmissionIsAcceptedWithServerGeneratedId() throws Exception {
    Map<String, Object> body = Payloads.external();
    body.put(
        "selections",
        Payloads.selections(
            List.of("ws-alpha", "ws-beta"), List.of("ev-gala"), List.of("meal-veg"), List.of()));
    String clientRequestId = (String) body.get("clientRequestId");

    Api.Response r = api.postExternal(body);

    assertThat(r.status()).as(r.toString()).isEqualTo(201);
    JsonNode accepted = r.json();
    String registrationId = accepted.path("registrationId").asText();
    assertThat(registrationId).matches("[0-9a-f-]{36}").isNotEqualTo(clientRequestId);
    assertThat(accepted.path("clientRequestId").asText()).isEqualTo(clientRequestId);
    assertThat(accepted.path("formType").asText()).isEqualTo("external");
    assertThat(accepted.path("status").asText()).isEqualTo("ACCEPTED");

    Map<String, Object> row = app.store().registrationRow(registrationId);
    assertThat(row).containsEntry("email", body.get("email"));
    assertThat(row).containsEntry("organization", "Synthetic Institute d.o.o.");
    assertThat(row).containsEntry("consent_given", true);
    JsonNode record =
        new ObjectMapper().readTree(Files.readAllBytes(app.store().jsonFile(registrationId)));
    assertThat(record.path("selections").path("workshops").findValuesAsText("id"))
        .containsExactly("ws-alpha", "ws-beta");
    assertThat(record.path("selections").path("meals").findValuesAsText("id"))
        .containsExactly("meal-veg");
    assertThat(record.path("selections").path("other").size()).isZero();
  }

  @Test
  void ac_001_01_submissionWithNoSelectionsIsAccepted() {
    Map<String, Object> body = Payloads.external();
    body.put("selections", Payloads.selections(List.of(), List.of(), List.of(), List.of()));
    assertThat(api.postExternal(body).status()).isEqualTo(201);
  }

  static Stream<Arguments> requiredFieldViolations() {
    Stream.Builder<Arguments> b = Stream.builder();
    for (String field : List.of("firstName", "lastName", "email", "organization")) {
      b.add(Arguments.of(field, null));
      b.add(Arguments.of(field, ""));
      b.add(Arguments.of(field, "   "));
      b.add(Arguments.of(field, "  "));
      b.add(Arguments.of(field, "  \t  "));
    }
    return b.build();
  }

  @ParameterizedTest(name = "AC-001-02 {0}=[{1}]")
  @MethodSource("requiredFieldViolations")
  void ac_001_02_missingOrBlankRequiredFieldIsRejected(String field, String value) {
    Map<String, Object> body = Payloads.external();
    if (value == null) {
      body.remove(field);
    } else {
      body.put(field, value);
    }
    Store.Snapshot before = app.store().snapshot();

    Api.Response r = api.postExternal(body);

    assertThat(r.status()).as(r.toString()).isEqualTo(400);
    assertThat(r.hasFieldError(field, "REQUIRED")).as(r.toString()).isTrue();
    assertNothingStored(app, before, "email".equals(field) ? null : (String) body.get("email"));
  }

  @ParameterizedTest(name = "AC-001-03 email=[{0}]")
  @ValueSource(
      strings = {
        "plainaddress",
        "missing-at.example.test",
        "@example.test",
        "user@",
        "user@localhost",
        "user@@example.test",
        "user name@example.test",
        "user@exa mple.test",
        ".user@example.test",
        "user..dots@example.test"
      })
  void ac_001_03_invalidEmailIsRejected(String email) {
    Map<String, Object> body = Payloads.external();
    body.put("email", email);
    Store.Snapshot before = app.store().snapshot();

    Api.Response r = api.postExternal(body);

    assertThat(r.status()).as(r.toString()).isEqualTo(400);
    assertThat(r.hasFieldError("email", "INVALID_EMAIL")).as(r.toString()).isTrue();
    assertNothingStored(app, before, null);
  }

  @Test
  void ac_001_04_unicodeValuesAreTrimmedAndPreserved() throws Exception {
    Map<String, Object> body = Payloads.external();
    body.put("firstName", "  Žiga Čedomir ");
    body.put("lastName", " Šuštaršič-Ćosić ");
    body.put("organization", " Inštitut za čebelarstvo – Đakovo ");
    String email = (String) body.get("email");
    body.put("email", " " + email + " ");

    Api.Response r = api.postExternal(body);

    assertThat(r.status()).as(r.toString()).isEqualTo(201);
    String id = r.json().path("registrationId").asText();
    JsonNode participant =
        new ObjectMapper()
            .readTree(Files.readAllBytes(app.store().jsonFile(id)))
            .path("participant");
    assertThat(participant.path("firstName").asText()).isEqualTo("Žiga Čedomir");
    assertThat(participant.path("lastName").asText()).isEqualTo("Šuštaršič-Ćosić");
    assertThat(participant.path("organization").asText())
        .isEqualTo("Inštitut za čebelarstvo – Đakovo");
    assertThat(participant.path("email").asText()).isEqualTo(email);
    assertThat(app.store().registrationRow(id)).containsEntry("first_name", "Žiga Čedomir");
  }

  static Stream<Arguments> invalidSelections() {
    return Stream.of(
        Arguments.of(
            "unknown", Payloads.selections(List.of("ws-nope"), List.of(), List.of(), List.of())),
        Arguments.of(
            "inactive",
            Payloads.selections(List.of("ws-inactive"), List.of(), List.of(), List.of())),
        Arguments.of(
            "inactive event",
            Payloads.selections(List.of(), List.of("ev-off"), List.of(), List.of())),
        Arguments.of(
            "wrong group",
            Payloads.selections(List.of("ev-gala"), List.of(), List.of(), List.of())),
        Arguments.of(
            "duplicate",
            Payloads.selections(List.of("ws-alpha", "ws-alpha"), List.of(), List.of(), List.of())),
        Arguments.of("unknown group", Map.of("tours", List.of("ws-alpha"))));
  }

  @ParameterizedTest(name = "AC-001-05 {0}")
  @MethodSource("invalidSelections")
  void ac_001_05_unknownInactiveWrongGroupOrDuplicateOptionIsRejected(
      String label, Object selections) {
    Map<String, Object> body = Payloads.external();
    body.put("selections", selections);
    Store.Snapshot before = app.store().snapshot();

    Api.Response r = api.postExternal(body);

    assertThat(r.status()).as(label + " " + r).isEqualTo(400);
    assertThat(r.json().path("errors").findValuesAsText("field"))
        .as(r.toString())
        .anyMatch(f -> f.startsWith("selections"));
    assertNothingStored(app, before, (String) body.get("email"));
  }

  @ParameterizedTest(name = "AC-001-06 captcha=[{0}]")
  @ValueSource(strings = {"<missing>", "", "wrong-token", "LOCAL-CAPTCHA-OK"})
  void ac_001_06_missingOrInvalidCaptchaIsRejected(String token) {
    Map<String, Object> body = Payloads.external();
    if ("<missing>".equals(token)) {
      body.remove("captchaToken");
    } else {
      body.put("captchaToken", token);
    }
    Store.Snapshot before = app.store().snapshot();

    Api.Response r = api.postExternal(body);

    assertThat(r.status()).as(r.toString()).isEqualTo(400);
    assertThat(r.hasFieldError("captchaToken", null)).as(r.toString()).isTrue();
    assertNothingStored(app, before, (String) body.get("email"));
  }

  @Test
  void ac_001_07_absentOrFalseConsentIsRejected() {
    for (Object consent : new Object[] {null, false}) {
      Map<String, Object> body = Payloads.external();
      if (consent == null) {
        body.remove("consentGiven");
      } else {
        body.put("consentGiven", consent);
      }
      Store.Snapshot before = app.store().snapshot();

      Api.Response r = api.postExternal(body);

      assertThat(r.status()).as(r.toString()).isEqualTo(400);
      assertThat(r.hasFieldError("consentGiven", null)).as(r.toString()).isTrue();
      assertNothingStored(app, before, (String) body.get("email"));
    }
  }

  @Test
  void ac_001_07_givenConsentIsAcceptedAndStored() throws Exception {
    Api.Response r = api.postExternal(Payloads.external());
    assertThat(r.status()).isEqualTo(201);
    String id = r.json().path("registrationId").asText();
    JsonNode consent =
        new ObjectMapper().readTree(Files.readAllBytes(app.store().jsonFile(id))).path("consent");
    assertThat(consent.path("id").asText()).isEqualTo("test-consent");
    assertThat(consent.path("given").asBoolean()).isTrue();
  }

  @Test
  void ac_001_08_fieldsAtMaximumLengthAreAccepted() {
    Map<String, Object> body = Payloads.external();
    body.put("firstName", "Š".repeat(100));
    body.put("lastName", "L".repeat(100));
    body.put("organization", "Ž".repeat(200));
    assertThat(api.postExternal(body).status()).isEqualTo(201);
  }

  static Stream<Arguments> overLongFields() {
    return Stream.of(
        Arguments.of("firstName", "Š".repeat(101)),
        Arguments.of("lastName", "x".repeat(101)),
        Arguments.of("organization", "o".repeat(201)),
        Arguments.of(
            "email",
            "a".repeat(64)
                + "@"
                + "d".repeat(63)
                + "."
                + "e".repeat(63)
                + "."
                + "f".repeat(63)
                + ".test"));
  }

  @ParameterizedTest(name = "AC-001-08 {0} too long")
  @MethodSource("overLongFields")
  void ac_001_08_overLongFieldIsRejected(String field, String value) {
    Map<String, Object> body = Payloads.external();
    body.put(field, value);
    Store.Snapshot before = app.store().snapshot();

    Api.Response r = api.postExternal(body);

    assertThat(r.status()).as(r.toString()).isEqualTo(400);
    assertThat(r.hasFieldError(field, null)).as(r.toString()).isTrue();
    assertNothingStored(app, before, null);
  }

  @Test
  void ac_001_08_oversizedRequestBodyIsRejected() {
    Map<String, Object> body = Payloads.external();
    body.put("captchaToken", "x".repeat(20_000));
    Store.Snapshot before = app.store().snapshot();

    Api.Response r = api.postJson("/api/registrations/external", Api.toJson(body));

    assertThat(r.status()).as(r.toString()).isIn(400, 413);
    assertNothingStored(app, before, (String) body.get("email"));
  }
}

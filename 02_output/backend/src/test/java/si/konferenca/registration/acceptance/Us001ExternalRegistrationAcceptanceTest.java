package si.konferenca.registration.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static si.konferenca.registration.acceptance.support.Registrations.external;
import static si.konferenca.registration.acceptance.support.Registrations.with;
import static si.konferenca.registration.acceptance.support.Registrations.without;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import si.konferenca.registration.acceptance.support.AcceptanceStack;
import si.konferenca.registration.acceptance.support.Api;
import si.konferenca.registration.acceptance.support.Json;
import si.konferenca.registration.acceptance.support.RecaptchaMock;
import si.konferenca.registration.acceptance.support.Response;
import si.konferenca.registration.acceptance.support.Storage;
import tools.jackson.databind.JsonNode;

/** US-001 External participant registration, through the REST API. */
class Us001ExternalRegistrationAcceptanceTest {

  private final Api api = AcceptanceStack.shared().api();

  @Test
  @DisplayName("AC-001-01 a complete external registration is accepted")
  void ac001_01_validExternalRegistrationIsAccepted() {
    Map<String, Object> request = external();

    Response response = api.register(request);

    assertThat(response.status()).as(response.toString()).isEqualTo(201);
    JsonNode body = response.json();
    UUID id = UUID.fromString(body.path("id").asString());
    assertThat(body.path("receivedAt").asString()).isNotBlank();
    Map<String, Object> row = AcceptanceStack.db().registrationById(id).orElseThrow();
    assertThat(row).containsEntry("type", "EXTERNAL").containsEntry("email", request.get("email"));
  }

  @ParameterizedTest(name = "AC-001-03 empty required field {0} is rejected")
  @ValueSource(strings = {"firstName", "lastName", "email", "organization"})
  void ac001_03_emptyRequiredFieldIsRejected(String field) {
    Storage.Snapshot before = Storage.snapshot();

    Response response = api.register(with(external(), field, ""));

    assertThat(response.hasFieldError(field, "required")).as(response.toString()).isTrue();
    before.assertUnchanged();
  }

  @ParameterizedTest(name = "AC-001-03 missing required field {0} is rejected")
  @ValueSource(strings = {"firstName", "lastName", "email", "organization"})
  void ac001_03_missingRequiredFieldIsRejected(String field) {
    Storage.Snapshot before = Storage.snapshot();

    Response response = api.register(without(external(), field));

    assertThat(response.hasFieldError(field, "required")).as(response.toString()).isTrue();
    before.assertUnchanged();
  }

  @ParameterizedTest(name = "AC-001-04 whitespace-only value [{0}] counts as empty")
  @ValueSource(strings = {" ", " ", "  \t ", "  "})
  void ac001_04_whitespaceOnlyIncludingNoBreakSpaceIsRejected(String blank) {
    Storage.Snapshot before = Storage.snapshot();

    Response response =
        api.register(with(with(external(), "firstName", blank), "organization", blank));

    assertThat(response.hasFieldError("firstName", "required")).as(response.toString()).isTrue();
    assertThat(response.hasFieldError("organization", "required")).as(response.toString()).isTrue();
    before.assertUnchanged();
  }

  @Test
  @DisplayName("AC-001-05 leading and trailing whitespace, including NBSP, is not stored")
  void ac001_05_surroundingWhitespaceIsTrimmedBeforeStoring() throws Exception {
    Map<String, Object> request = external();
    String email = (String) request.get("email");
    request.put("firstName", "  Ana  ");
    request.put("lastName", "\tNovak ");
    request.put("organization", " Institut Primer ");
    request.put("email", " " + email + " ");

    Response response = api.register(request);

    assertThat(response.status()).as(response.toString()).isEqualTo(201);
    UUID id = UUID.fromString(response.json().path("id").asString());
    Map<String, Object> row = AcceptanceStack.db().registrationById(id).orElseThrow();
    assertThat(row)
        .containsEntry("first_name", "Ana")
        .containsEntry("last_name", "Novak")
        .containsEntry("organization", "Institut Primer")
        .containsEntry("email", email);
    JsonNode copy = readCopy(id);
    assertThat(copy.path("participant").path("firstName").asString()).isEqualTo("Ana");
    assertThat(copy.path("participant").path("organization").asString())
        .isEqualTo("Institut Primer");
  }

  @ParameterizedTest(name = "AC-001-06 invalid email [{0}] is rejected")
  @ValueSource(
      strings = {
        "plainaddress",
        "ana@",
        "@example.si",
        "ana@example",
        "ana novak@example.si",
        "ana@@example.si",
        "ana@example..si"
      })
  void ac001_06_invalidEmailIsRejected(String email) {
    Storage.Snapshot before = Storage.snapshot();

    Response response = api.register(with(external(), "email", email));

    assertThat(response.hasFieldError("email", "invalid_email")).as(response.toString()).isTrue();
    before.assertUnchanged();
  }

  @Test
  @DisplayName("AC-001-07 Slovenian and other Unicode letters are stored unchanged")
  void ac001_07_unicodeTextIsStoredUnchanged() throws Exception {
    Map<String, Object> request = external();
    request.put("firstName", "Čedomir Žan");
    request.put("lastName", "Šuštaršič-Ćirić");
    request.put("organization", "Društvo ŠČŽ, Müller & Ørsted");

    Response response = api.register(request);

    assertThat(response.status()).as(response.toString()).isEqualTo(201);
    UUID id = UUID.fromString(response.json().path("id").asString());
    assertThat(AcceptanceStack.db().registrationById(id).orElseThrow())
        .containsEntry("first_name", "Čedomir Žan")
        .containsEntry("last_name", "Šuštaršič-Ćirić")
        .containsEntry("organization", "Društvo ŠČŽ, Müller & Ørsted");
    assertThat(readCopy(id).path("participant").path("lastName").asString())
        .isEqualTo("Šuštaršič-Ćirić");
  }

  @Test
  @DisplayName("AC-001-08 the form offers only active options, each with its category")
  void ac001_08_formOffersOnlyActiveOptionsGroupedByCategory() {
    Response response = api.getForm();

    assertThat(response.status()).as(response.toString()).isEqualTo(200);
    List<String> ids = new ArrayList<>();
    for (JsonNode option : response.json().path("options")) {
      ids.add(option.path("id").asString());
      assertThat(option.path("category").asString()).isIn("WORKSHOP", "EVENT", "MEAL", "OTHER");
    }
    assertThat(ids)
        .contains("ws-testing", "ws-security", "ev-reception", "meal-dinner", "other-city-tour")
        .doesNotContain("ws-legacy");
    JsonNode reception = optionById(response.json(), "ev-reception");
    assertThat(reception.path("name").asString()).isEqualTo("Welcome reception");
    assertThat(reception.path("category").asString()).isEqualTo("EVENT");
  }

  @Test
  @DisplayName("AC-001-09 an inactive option is rejected")
  void ac001_09_inactiveOptionIsRejected() {
    Storage.Snapshot before = Storage.snapshot();

    Response response = api.register(with(external(), "optionIds", List.of("ws-legacy")));

    assertThat(response.hasFieldError("optionIds", "inactive_option"))
        .as(response.toString())
        .isTrue();
    before.assertUnchanged();
  }

  @Test
  @DisplayName("AC-001-10 an unknown option is rejected")
  void ac001_10_unknownOptionIsRejected() {
    Storage.Snapshot before = Storage.snapshot();

    Response response =
        api.register(with(external(), "optionIds", List.of("ws-testing", "no-such-option")));

    assertThat(response.hasFieldError("optionIds", "unknown_option"))
        .as(response.toString())
        .isTrue();
    before.assertUnchanged();
  }

  @Test
  @DisplayName("AC-001-11 more options in a category than its maximum are rejected")
  void ac001_11_tooManyOptionsInOneCategoryAreRejected() {
    Storage.Snapshot before = Storage.snapshot();

    Response response =
        api.register(with(external(), "optionIds", List.of("ws-testing", "ws-security")));

    assertThat(response.hasFieldError("optionIds", "too_many_options"))
        .as(response.toString())
        .isTrue();
    before.assertUnchanged();
  }

  @Test
  @DisplayName("AC-001-12 each mandatory consent comes with its configured wording")
  void ac001_12_formProvidesMandatoryConsentWithWording() {
    Response response = api.getForm();

    assertThat(response.status()).as(response.toString()).isEqualTo(200);
    JsonNode consents = response.json().path("consents");
    assertThat(consents.size()).isEqualTo(1);
    assertThat(consents.get(0).path("id").asString()).isEqualTo("data-processing");
    assertThat(consents.get(0).path("mandatory").asBoolean()).isTrue();
    assertThat(consents.get(0).path("text").asString())
        .startsWith("I agree that my personal data is processed");
  }

  @Test
  @DisplayName("AC-001-13 a registration without the mandatory consent is rejected")
  void ac001_13_missingMandatoryConsentIsRejected() {
    Storage.Snapshot before = Storage.snapshot();

    Response response = api.register(with(external(), "consentIds", List.of()));

    assertThat(response.hasFieldError("consentIds", "consent_missing"))
        .as(response.toString())
        .isTrue();
    before.assertUnchanged();
  }

  @ParameterizedTest(name = "AC-001-14 anti-automation token [{0}] is rejected")
  @ValueSource(strings = {"invalid-token", ""})
  void ac001_14_failedAntiAutomationCheckIsRejected(String token) {
    Storage.Snapshot before = Storage.snapshot();

    Response response = api.register(with(external(), "recaptchaToken", token));

    assertThat(response.hasFieldError("recaptchaToken", "captcha_failed"))
        .as(response.toString())
        .isTrue();
    before.assertUnchanged();
  }

  @Test
  @DisplayName("AC-001-14 the token is verified by the backend with the secret key")
  void ac001_14_tokenIsVerifiedServerSide() {
    int before = AcceptanceStack.recaptcha().requests().size();

    Response response = api.register(external());

    assertThat(response.status()).as(response.toString()).isEqualTo(201);
    List<Map<String, String>> calls = AcceptanceStack.recaptcha().requests();
    assertThat(calls).hasSizeGreaterThan(before);
    assertThat(calls.get(calls.size() - 1))
        .containsEntry("secret", RecaptchaMock.SECRET_KEY)
        .containsEntry("response", RecaptchaMock.VALID_TOKEN);
  }

  @Test
  @DisplayName("AC-001-15 a second registration with the same email is rejected")
  void ac001_15_duplicateEmailIsRejected() {
    Map<String, Object> first = external();
    String email = (String) first.get("email");
    assertThat(api.register(first).status()).isEqualTo(201);
    Storage.Snapshot before = Storage.snapshot();

    Response response = api.register(with(external(), "email", " " + email.toUpperCase() + " "));

    assertThat(response.status()).as(response.toString()).isEqualTo(409);
    assertThat(response.contentType()).startsWith("application/problem+json");
    before.assertUnchanged();
  }

  static JsonNode optionById(JsonNode form, String id) {
    for (JsonNode option : form.path("options")) {
      if (id.equals(option.path("id").asString())) {
        return option;
      }
    }
    throw new AssertionError("option " + id + " not offered");
  }

  static JsonNode readCopy(UUID id) throws Exception {
    Path file = AcceptanceStack.jsonCopyDir().resolve(id + ".json");
    assertThat(file).exists();
    return Json.read(Files.readString(file, StandardCharsets.UTF_8));
  }
}

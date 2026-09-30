package si.konferenca.registration.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static si.konferenca.registration.acceptance.support.Checks.assertFieldError;
import static si.konferenca.registration.acceptance.support.Checks.assertNothingStored;
import static si.konferenca.registration.acceptance.support.Payloads.EVENT;
import static si.konferenca.registration.acceptance.support.Payloads.INACTIVE_WORKSHOP;
import static si.konferenca.registration.acceptance.support.Payloads.MEAL;
import static si.konferenca.registration.acceptance.support.Payloads.MEAL_NAME;
import static si.konferenca.registration.acceptance.support.Payloads.NEWSLETTER;
import static si.konferenca.registration.acceptance.support.Payloads.PRIVACY;
import static si.konferenca.registration.acceptance.support.Payloads.WORKSHOP;
import static si.konferenca.registration.acceptance.support.Payloads.WORKSHOP_NAME;
import static si.konferenca.registration.acceptance.support.Payloads.external;
import static si.konferenca.registration.acceptance.support.Payloads.with;
import static si.konferenca.registration.acceptance.support.Payloads.without;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import si.konferenca.registration.acceptance.support.AcceptanceTest;
import si.konferenca.registration.acceptance.support.Api;
import si.konferenca.registration.acceptance.support.Database;
import si.konferenca.registration.acceptance.support.Payloads;
import tools.jackson.databind.JsonNode;

/** US-001 external participant registration through the REST API. */
class ExternalRegistrationAcceptanceTest extends AcceptanceTest {

  @Test
  @DisplayName("AC-001-01 a valid external registration is accepted with 201 and stored")
  void ac001_01_acceptsValidExternalRegistration() {
    Map<String, Object> payload = external();
    String email = (String) payload.get("email");

    Api.Response response = api.register(payload);

    assertThat(response.status()).as(response.text()).isEqualTo(201);
    JsonNode body = response.json();
    assertThat(UUID.fromString(body.get("id").asString())).isNotNull();
    assertThat(body.get("type").asString()).isEqualTo("EXTERNAL");
    assertThat(body.get("firstName").asString()).isEqualTo("Ana");
    assertThat(body.get("lastName").asString()).isEqualTo("Novak");
    assertThat(body.get("email").asString()).isEqualTo(email);
    assertThat(body.get("organization").asString()).isEqualTo("Institut Jožef Stefan");
    assertThat(body.get("submittedAt").asString()).isNotBlank();
    List<String> optionIds = new ArrayList<>();
    List<String> optionNames = new ArrayList<>();
    body.get("options")
        .forEach(
            o -> {
              optionIds.add(o.get("id").asString());
              optionNames.add(o.get("name").asString());
            });
    assertThat(optionIds).containsExactly(WORKSHOP, MEAL);
    assertThat(optionNames).containsExactly(WORKSHOP_NAME, MEAL_NAME);
    assertThat(body.get("consents").get(0).get("id").asString()).isEqualTo(PRIVACY);
    assertThat(body.has("recaptchaToken")).isFalse();
    assertThat(Database.countByEmail(email)).isEqualTo(1);
  }

  static Stream<Arguments> missingExternalFields() {
    List<Arguments> args = new ArrayList<>();
    for (String field : List.of("firstName", "lastName", "email", "organization")) {
      args.add(Arguments.of(field, null));
      args.add(Arguments.of(field, ""));
      args.add(Arguments.of(field, "   "));
    }
    return args.stream();
  }

  @ParameterizedTest(name = "AC-001-02 {0} = [{1}] is rejected")
  @MethodSource("missingExternalFields")
  @DisplayName("AC-001-02 a missing, empty or blank required field is rejected")
  void ac001_02_rejectsMissingRequiredField(String field, String value) {
    Map<String, Object> payload = external();
    String email = (String) payload.get("email");
    Map<String, Object> sent =
        value == null ? without(payload, field) : with(payload, field, value);

    Api.Response response = api.register(sent);

    assertFieldError(response, 400, field);
    assertNothingStored(email, jsonDir());
  }

  @Test
  @DisplayName("AC-001-03 leading and trailing whitespace is removed before storage")
  void ac001_03_trimsWhitespace() {
    Map<String, Object> payload = external();
    String email = (String) payload.get("email");
    payload.put("firstName", "  Ana ");
    payload.put("lastName", "\tNovak  ");
    payload.put("email", "  " + email + " ");
    payload.put("organization", " Institut Jožef Stefan  ");

    Api.Response response = api.register(payload);

    assertThat(response.status()).as(response.text()).isEqualTo(201);
    JsonNode body = response.json();
    assertThat(body.get("firstName").asString()).isEqualTo("Ana");
    assertThat(body.get("lastName").asString()).isEqualTo("Novak");
    assertThat(body.get("email").asString()).isEqualTo(email);
    assertThat(body.get("organization").asString()).isEqualTo("Institut Jožef Stefan");
    Map<String, Object> row = Database.registrationByEmail(email);
    assertThat(row).isNotNull();
    assertThat(row.get("first_name")).isEqualTo("Ana");
    assertThat(row.get("last_name")).isEqualTo("Novak");
    assertThat(row.get("email")).isEqualTo(email);
    assertThat(row.get("organization")).isEqualTo("Institut Jožef Stefan");
  }

  @ParameterizedTest(name = "AC-001-04 email [{0}] is rejected")
  @ValueSource(strings = {"plainaddress", "ana@", "@example.si", "ana novak@example.si", "ana@si"})
  @DisplayName("AC-001-04 an invalid email address is rejected")
  void ac001_04_rejectsInvalidEmail(String invalid) {
    Map<String, Object> payload = with(external(), "email", invalid);

    Api.Response response = api.register(payload);

    assertFieldError(response, 400, "email");
    assertNothingStored(invalid, jsonDir());
  }

  static Stream<Arguments> invalidOptionSets() {
    return Stream.of(
        Arguments.of("unknown option", List.of(WORKSHOP, "does-not-exist")),
        Arguments.of("inactive option", List.of(INACTIVE_WORKSHOP)),
        Arguments.of("duplicate option", List.of(EVENT, EVENT)));
  }

  @ParameterizedTest(name = "AC-001-05 {0} is rejected")
  @MethodSource("invalidOptionSets")
  @DisplayName("AC-001-05 unknown, inactive or duplicate options are rejected")
  void ac001_05_rejectsInvalidOptions(String description, List<String> optionIds) {
    Map<String, Object> payload = with(external(), "optionIds", optionIds);
    String email = (String) payload.get("email");

    Api.Response response = api.register(payload);

    assertFieldError(response, 400, "optionIds");
    assertNothingStored(email, jsonDir());
  }

  static Stream<Arguments> invalidConsents() {
    return Stream.of(
        Arguments.of("no consent given", List.of()),
        Arguments.of("only the optional consent", List.of(NEWSLETTER)),
        Arguments.of("unknown consent", List.of(PRIVACY, "marketing")),
        Arguments.of("consents omitted", null));
  }

  @ParameterizedTest(name = "AC-001-06 {0} is rejected")
  @MethodSource("invalidConsents")
  @DisplayName("AC-001-06 a missing mandatory consent or an unknown consent is rejected")
  void ac001_06_rejectsMissingConsent(String description, List<String> consents) {
    Map<String, Object> base = external();
    String email = (String) base.get("email");
    Map<String, Object> payload =
        consents == null ? without(base, "consents") : with(base, "consents", consents);

    Api.Response response = api.register(payload);

    assertFieldError(response, 400, "consents");
    assertNothingStored(email, jsonDir());
  }

  @ParameterizedTest(name = "AC-001-07 token [{0}] is rejected")
  @ValueSource(strings = {"", "wrong-token", "TEST-MODE-PASS"})
  @DisplayName("AC-001-07 in test mode a token other than the pass token is rejected")
  void ac001_07_rejectsWrongTestModeToken(String token) {
    Map<String, Object> payload = with(external(), "recaptchaToken", token);
    String email = (String) payload.get("email");

    Api.Response response = api.register(payload);

    assertFieldError(response, 400, "recaptchaToken");
    assertNothingStored(email, jsonDir());
  }

  @Test
  @DisplayName("AC-001-07 in test mode a missing token is rejected")
  void ac001_07_rejectsMissingToken() {
    Map<String, Object> payload = without(external(), "recaptchaToken");
    String email = (String) payload.get("email");

    Api.Response response = api.register(payload);

    assertFieldError(response, 400, "recaptchaToken");
    assertNothingStored(email, jsonDir());
  }

  @Test
  @DisplayName("AC-001-09 a second registration with the same email in another case is rejected")
  void ac001_09_rejectsDuplicateEmail() {
    Map<String, Object> first = external();
    String email = (String) first.get("email");
    assertThat(api.register(first).status()).isEqualTo(201);
    Map<String, Object> before = Database.registrationByEmail(email);

    Map<String, Object> second =
        with(
            with(external(), "email", email.toUpperCase(java.util.Locale.ROOT)),
            "firstName",
            "Bojan");
    Api.Response response = api.register(second);

    assertFieldError(response, 409, "email");
    assertThat(response.json().path("error").asString()).isEqualTo("duplicate_registration");
    assertThat(Database.countByEmail(email)).isEqualTo(1);
    assertThat(Database.registrationByEmail(email)).isEqualTo(before);
  }

  @Test
  @DisplayName("AC-001-11 a request body over the size limit is rejected with 413")
  void ac001_11_rejectsOversizedBody() {
    Map<String, Object> payload = external();
    String email = (String) payload.get("email");
    payload.put("organization", "x".repeat(20_000));

    Api.Response response = api.register(payload);

    assertThat(response.status()).as(response.text()).isEqualTo(413);
    assertThat(response.json().path("error").asString()).isEqualTo("payload_too_large");
    assertNothingStored(email, jsonDir());
  }

  @Test
  @DisplayName("AC-001-13 Slovenian characters are accepted and kept unchanged")
  void ac001_13_keepsSlovenianCharacters() {
    Map<String, Object> payload = external();
    String email = (String) payload.get("email");
    payload.put("firstName", "Čedomir Žiga");
    payload.put("lastName", "Šuštaršič");
    payload.put("organization", "Fakulteta za računalništvo, ŠČŽ čšž");

    Api.Response response = api.register(payload);

    assertThat(response.status()).as(response.text()).isEqualTo(201);
    assertThat(response.json().get("firstName").asString()).isEqualTo("Čedomir Žiga");
    assertThat(response.json().get("lastName").asString()).isEqualTo("Šuštaršič");
    Map<String, Object> row = Database.registrationByEmail(email);
    assertThat(row.get("first_name")).isEqualTo("Čedomir Žiga");
    assertThat(row.get("last_name")).isEqualTo("Šuštaršič");
    assertThat(row.get("organization")).isEqualTo("Fakulteta za računalništvo, ŠČŽ čšž");
  }

  static Stream<Arguments> foreignFields() {
    return Stream.of(
        Arguments.of("studentId", "63210001"),
        Arguments.of("studyInstitution", "Univerza v Mariboru"),
        Arguments.of("studyProgramme", "Informatika"),
        Arguments.of("nickname", "ana"));
  }

  @ParameterizedTest(name = "AC-001-14 extra field {0} is rejected")
  @MethodSource("foreignFields")
  @DisplayName("AC-001-14 fields outside the external form are rejected")
  void ac001_14_rejectsForeignFields(String field, String value) {
    Map<String, Object> payload = with(external(), field, value);
    String email = (String) payload.get("email");

    Api.Response response = api.register(payload);

    assertThat(response.status()).as(response.text()).isEqualTo(400);
    assertNothingStored(email, jsonDir());
  }

  @Test
  @DisplayName("AC-001-14 a body that is not valid JSON is rejected")
  void ac001_14_rejectsMalformedJson() {
    String email = Payloads.uniqueEmail();

    Api.Response response =
        api.postJson(
            "/api/registrations",
            "{\"type\":\"EXTERNAL\",\"email\":\"" + email + "\"",
            "X-Forwarded-For",
            si.konferenca.registration.acceptance.support.Clients.next());

    assertThat(response.status()).isEqualTo(400);
    assertNothingStored(email, jsonDir());
  }
}

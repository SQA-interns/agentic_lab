package si.konferenca.registration.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static si.konferenca.registration.acceptance.support.Registrations.consents;
import static si.konferenca.registration.acceptance.support.Registrations.emailOfLength;
import static si.konferenca.registration.acceptance.support.Registrations.external;
import static si.konferenca.registration.acceptance.support.Registrations.letters;
import static si.konferenca.registration.acceptance.support.Registrations.options;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import si.konferenca.registration.acceptance.support.AcceptanceTest;
import si.konferenca.registration.acceptance.support.ApiClient;
import si.konferenca.registration.acceptance.support.Registrations;
import tools.jackson.databind.node.ObjectNode;

/** US-001 External participant registration, through the REST API. */
class ExternalRegistrationAcceptanceTest extends AcceptanceTest {

  @Test
  void ac_001_01_validExternalRegistrationIsAcceptedWithExactlyTheGivenDataAndOptions() {
    ObjectNode request = external();
    options(request, "ws-ai", "ev-reception", "meal-lunch-1");

    ApiClient.Response response = api.register(request);

    assertThat(response.status()).as(response.text()).isEqualTo(201);
    assertThat(response.json().path("type").asString()).isEqualTo("EXTERNAL");
    String id = response.json().path("registrationId").asString();
    assertThat(UUID.fromString(id)).isNotNull();
    Map<String, Object> row =
        db.registrationByEmail(request.path("email").asString()).orElseThrow();
    assertThat(row.get("id").toString()).isEqualTo(id);
    assertThat(row.get("type")).isEqualTo("EXTERNAL");
    assertThat(row.get("first_name")).isEqualTo("Ana");
    assertThat(row.get("last_name")).isEqualTo("Novak");
    assertThat(row.get("email")).isEqualTo(request.path("email").asString());
    assertThat(row.get("organization")).isEqualTo("Institut Jožef Stefan");
    assertThat(row.get("study_institution")).isNull();
    assertThat(row.get("study_programme")).isNull();
    assertThat(row.get("student_id")).isNull();
    assertThat(db.options(row.get("id")))
        .extracting(option -> option.get("option_id"))
        .containsExactly("ev-reception", "meal-lunch-1", "ws-ai");
  }

  @ParameterizedTest(name = "AC-001-03 {0} = \"{1}\"")
  @CsvSource(
      value = {
        "firstName|''",
        "firstName|'   '",
        "lastName|''",
        "lastName|'      '",
        "email|''",
        "email|'   '",
        "organization|''",
        "organization|'   '"
      },
      delimiter = '|')
  void ac_001_03_emptyOrBlankRequiredFieldIsRejectedNamingTheField(String field, String value) {
    ObjectNode request = external();
    request.put(field, value);

    ApiClient.Response response = api.register(request);

    assertFieldError(response, field, "required");
    assertNothingStoredOrSent();
  }

  @ParameterizedTest(name = "AC-001-03 {0} absent")
  @ValueSource(strings = {"firstName", "lastName", "email", "organization"})
  void ac_001_03_absentRequiredFieldIsRejectedNamingTheField(String field) {
    ObjectNode request = external();
    request.remove(field);

    ApiClient.Response response = api.register(request);

    assertFieldError(response, field, "required");
    assertNothingStoredOrSent();
  }

  @Test
  void ac_001_04_leadingAndTrailingWhitespaceIsNotStored() {
    ObjectNode request = external();
    String email = request.path("email").asString();
    request.put("firstName", "  Ana ");
    request.put("lastName", "\tNovak  ");
    request.put("email", "  " + email + " ");
    request.put("organization", " Institut Jožef Stefan  ");

    ApiClient.Response response = api.register(request);

    assertThat(response.status()).as(response.text()).isEqualTo(201);
    Map<String, Object> row = db.registrationByEmail(email).orElseThrow();
    assertThat(row.get("first_name")).isEqualTo("Ana");
    assertThat(row.get("last_name")).isEqualTo("Novak");
    assertThat(row.get("email")).isEqualTo(email);
    assertThat(row.get("organization")).isEqualTo("Institut Jožef Stefan");
  }

  @ParameterizedTest(name = "AC-001-05 email \"{0}\"")
  @ValueSource(
      strings = {
        "not-an-email",
        "ana@example",
        "ana novak@example.si",
        "@example.si",
        "ana@",
        "ana@@example.si"
      })
  void ac_001_05_invalidEmailIsRejectedNamingTheEmailField(String email) {
    ObjectNode request = external();
    request.put("email", email);

    ApiClient.Response response = api.register(request);

    assertFieldError(response, "email", "invalid_email");
    assertNothingStoredOrSent();
  }

  @Test
  void ac_001_06_unicodeTextWithSlovenianCharactersIsStoredUnchanged() {
    ObjectNode request = external();
    request.put("firstName", "Črtomir Žiga");
    request.put("lastName", "Šuštaršič-Čeh");
    request.put("organization", "Univerza v Ljubljani, Fakulteta za računalništvo – ČŠŽ čšž ĆĐ");

    ApiClient.Response response = api.register(request);

    assertThat(response.status()).as(response.text()).isEqualTo(201);
    Map<String, Object> row =
        db.registrationByEmail(request.path("email").asString()).orElseThrow();
    assertThat(row.get("first_name")).isEqualTo("Črtomir Žiga");
    assertThat(row.get("last_name")).isEqualTo("Šuštaršič-Čeh");
    assertThat(row.get("organization"))
        .isEqualTo("Univerza v Ljubljani, Fakulteta za računalništvo – ČŠŽ čšž ĆĐ");
  }

  @Test
  void ac_001_07_unknownOptionIsRejected() {
    ObjectNode request = options(external(), "ws-ai", "no-such-option");

    ApiClient.Response response = api.register(request);

    assertFieldError(response, "optionIds", "unknown_option");
    assertNothingStoredOrSent();
  }

  @Test
  void ac_001_08_inactiveOptionIsRejected() {
    ObjectNode request = options(external(), "ws-legacy");

    ApiClient.Response response = api.register(request);

    assertFieldError(response, "optionIds", "inactive_option");
    assertNothingStoredOrSent();
  }

  @Test
  void ac_001_09_optionNotAvailableToExternalParticipantsIsRejected() {
    ObjectNode request = options(external(), "other-career-fair");

    ApiClient.Response response = api.register(request);

    assertFieldError(response, "optionIds", "option_not_available");
    assertNothingStoredOrSent();
  }

  @Test
  void ac_001_10_moreOptionsInOneCategoryThanAllowedIsRejected() {
    ObjectNode request = options(external(), "ws-ai", "ws-security");

    ApiClient.Response response = api.register(request);

    assertFieldError(response, "optionIds", "too_many_options");
    assertNothingStoredOrSent();
  }

  @Test
  void ac_001_10_selectionsUpToTheCategoryMaximumAreAccepted() {
    ObjectNode request =
        options(external(), "ev-reception", "ev-industry-dinner", "meal-lunch-1", "meal-lunch-2");

    ApiClient.Response response = api.register(request);

    assertThat(response.status()).as(response.text()).isEqualTo(201);
  }

  @Test
  void ac_001_11_registrationWithoutOptionsIsAccepted() {
    ObjectNode request = options(external());

    ApiClient.Response response = api.register(request);

    assertThat(response.status()).as(response.text()).isEqualTo(201);
    Map<String, Object> row =
        db.registrationByEmail(request.path("email").asString()).orElseThrow();
    assertThat(db.options(row.get("id"))).isEmpty();
  }

  @Test
  void ac_001_12_registrationWithoutTheMandatoryConsentIsRejected() {
    ObjectNode request = consents(external());

    ApiClient.Response response = api.register(request);

    assertFieldError(response, "consentIds", "consent_required");
    assertNothingStoredOrSent();
  }

  @ParameterizedTest(name = "AC-001-14 token \"{0}\"")
  @ValueSource(strings = {"wrong-token", ""})
  void ac_001_14_registrationWithoutAValidAntiAutomationProofIsRejected(String token) {
    ObjectNode request = external();
    request.put("captchaToken", token);

    ApiClient.Response response = api.register(request);

    assertThat(response.status()).as(response.text()).isEqualTo(400);
    assertThat(response.json().path("error").asString()).isEqualTo("captcha_failed");
    assertNothingStoredOrSent();
  }

  @Test
  void ac_001_14_registrationWithoutAnyAntiAutomationTokenIsRejected() {
    ObjectNode request = external();
    request.remove("captchaToken");

    ApiClient.Response response = api.register(request);

    assertThat(response.status()).as(response.text()).isEqualTo(400);
    assertThat(response.json().path("error").asString()).isEqualTo("captcha_failed");
    assertNothingStoredOrSent();
  }

  @Test
  void ac_001_15_secondRegistrationWithTheSameEmailInAnyLetterCaseIsRejected() {
    ObjectNode first = external();
    String email = Registrations.uniqueEmail("Dup.Case");
    first.put("email", email);
    assertThat(api.register(first).status()).isEqualTo(201);
    Map<String, Object> before = db.registrationByEmail(email).orElseThrow();
    mail.clear();

    ObjectNode second = external();
    second.put("firstName", "Other");
    second.put("email", email.toUpperCase());
    ApiClient.Response response = api.register(second);

    assertThat(response.status()).as(response.text()).isEqualTo(409);
    assertThat(response.json().path("error").asString()).isEqualTo("duplicate_email");
    assertThat(fieldErrors(response.json())).contains("email:duplicate_email");
    assertThat(db.countRegistrations()).isEqualTo(1);
    assertThat(db.registrationByEmail(email).orElseThrow()).isEqualTo(before);
    assertThat(copies.fileNames()).hasSize(1);
    assertNoEmail();
  }

  @ParameterizedTest(name = "AC-001-16 {0} longer than {1}")
  @CsvSource({"firstName, 100", "lastName, 100", "organization, 200"})
  void ac_001_16_textFieldLongerThanItsMaximumIsRejected(String field, int maximum) {
    ObjectNode request = external();
    request.put(field, letters(maximum + 1));

    ApiClient.Response response = api.register(request);

    assertFieldError(response, field, "too_long");
    assertNothingStoredOrSent();
  }

  @Test
  void ac_001_16_emailLongerThan254CharactersIsRejected() {
    ObjectNode request = external();
    request.put("email", emailOfLength(255));

    ApiClient.Response response = api.register(request);

    assertFieldError(response, "email", "too_long");
    assertNothingStoredOrSent();
  }

  @Test
  void ac_001_16_textFieldsOfExactlyTheMaximumLengthAreAccepted() {
    ObjectNode request = external();
    request.put("firstName", letters(100));
    request.put("lastName", letters(100));
    request.put("organization", letters(200));
    request.put("email", emailOfLength(254));

    ApiClient.Response response = api.register(request);

    assertThat(response.status()).as(response.text()).isEqualTo(201);
  }

  @ParameterizedTest(name = "AC-001-18 {0} with a control character")
  @CsvSource({"firstName, 0", "lastName, 1", "organization, 2", "firstName, 3"})
  void ac_001_18_controlCharacterInATextFieldIsRejected(String field, int variant) {
    String value = List.of("Ana\nMarija", "Novak\rX", "Inštitut\u0007", "Ana\tMarija").get(variant);
    ObjectNode request = external();
    request.put(field, value);

    ApiClient.Response response = api.register(request);

    assertFieldError(response, field, "invalid_characters");
    assertNothingStoredOrSent();
  }
}

package si.konferenca.registration.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static si.konferenca.registration.acceptance.support.Payloads.external;
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

/** US-001 External participant registration, through the REST API. */
class Us001ExternalRegistrationAcceptanceTest extends AcceptanceTestBase {

  @Test
  void AC_001_02_valid_external_registration_is_accepted_with_the_entered_values() {
    Map<String, Object> body = external();

    JsonNode accepted = assertAccepted(api.register(body));

    assertThat(accepted.path("type").asString()).isEqualTo("EXTERNAL");
    assertThat(accepted.path("firstName").asString()).isEqualTo("Janez");
    assertThat(accepted.path("lastName").asString()).isEqualTo("Novak");
    assertThat(accepted.path("email").asString()).isEqualTo(body.get("email"));
    assertThat(accepted.path("id").asString()).isNotBlank();
    Map<String, Object> row = db.registrationByEmail((String) body.get("email"));
    assertThat(row).isNotNull();
    assertThat(row.get("type")).isEqualTo("EXTERNAL");
    assertThat(row.get("organization")).isEqualTo("Institute of Testing");
  }

  @Test
  void AC_001_03_selected_active_options_are_accepted_exactly() {
    Map<String, Object> body =
        with(
            external(),
            "optionIds",
            List.of("ws-ai-research", "ev-gala-dinner", "other-city-tour"));

    JsonNode accepted = assertAccepted(api.register(body));

    assertThat(Json.strings(accepted.path("options"), "id"))
        .containsExactlyInAnyOrder("ws-ai-research", "ev-gala-dinner", "other-city-tour");
  }

  @Test
  void AC_001_03_registration_without_options_is_accepted() {
    JsonNode accepted = assertAccepted(api.register(with(external(), "optionIds", List.of())));

    assertThat(accepted.path("options").size()).isZero();
  }

  @ParameterizedTest
  @ValueSource(strings = {"firstName", "lastName", "email", "organization"})
  void AC_001_04_empty_required_field_is_rejected(String field) {
    assertFieldError(api.register(with(external(), field, "")), field, "REQUIRED");
    assertNothingStored();
  }

  @ParameterizedTest
  @ValueSource(strings = {"firstName", "lastName", "email", "organization"})
  void AC_001_04_whitespace_only_required_field_is_rejected(String field) {
    assertFieldError(api.register(with(external(), field, "   ")), field, "REQUIRED");
    assertNothingStored();
  }

  @ParameterizedTest
  @ValueSource(strings = {"firstName", "lastName", "email", "organization"})
  void AC_001_04_missing_required_field_is_rejected(String field) {
    assertFieldError(api.register(without(external(), field)), field, "REQUIRED");
    assertNothingStored();
  }

  @Test
  void AC_001_05_leading_and_trailing_whitespace_is_not_stored() {
    Map<String, Object> body = external();
    String email = (String) body.get("email");
    body.put("firstName", "  Janez ");
    body.put("lastName", " Novak  ");
    body.put("email", "  " + email + "  ");
    body.put("organization", " Institute of Testing  ");

    JsonNode accepted = assertAccepted(api.register(body));

    assertThat(accepted.path("firstName").asString()).isEqualTo("Janez");
    assertThat(accepted.path("lastName").asString()).isEqualTo("Novak");
    assertThat(accepted.path("email").asString()).isEqualTo(email);
    Map<String, Object> row = db.registrationByEmail(email);
    assertThat(row.get("first_name")).isEqualTo("Janez");
    assertThat(row.get("last_name")).isEqualTo("Novak");
    assertThat(row.get("email")).isEqualTo(email);
    assertThat(row.get("organization")).isEqualTo("Institute of Testing");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "janez",
        "janez@",
        "@example.com",
        "janez@example",
        "jan ez@example.com",
        "janez@@example.com",
        "janez@exa mple.com"
      })
  void AC_001_06_invalid_email_is_rejected(String email) {
    assertFieldError(api.register(with(external(), "email", email)), "email", "INVALID_FORMAT");
    assertNothingStored();
  }

  @Test
  void AC_001_07_slovenian_characters_are_kept_unchanged() {
    Map<String, Object> body = external();
    body.put("firstName", "Žiga");
    body.put("lastName", "Čebašek");
    body.put("organization", "Fakulteta za računalništvo, Šiška");

    JsonNode accepted = assertAccepted(api.register(body));

    assertThat(accepted.path("firstName").asString()).isEqualTo("Žiga");
    assertThat(accepted.path("lastName").asString()).isEqualTo("Čebašek");
    Map<String, Object> row = db.registrationByEmail((String) body.get("email"));
    assertThat(row.get("first_name")).isEqualTo("Žiga");
    assertThat(row.get("last_name")).isEqualTo("Čebašek");
    assertThat(row.get("organization")).isEqualTo("Fakulteta za računalništvo, Šiška");
  }

  @Test
  void AC_001_08_unknown_option_is_rejected() {
    Api.Response response =
        api.register(with(external(), "optionIds", List.of("ws-ai-research", "no-such-option")));

    assertFieldError(response, "optionIds", "UNKNOWN_OPTION");
    assertNothingStored();
  }

  @Test
  void AC_001_09_inactive_option_is_rejected() {
    assertFieldError(
        api.register(with(external(), "optionIds", List.of("ws-legacy"))),
        "optionIds",
        "INACTIVE_OPTION");
    assertNothingStored();
  }

  @Test
  void AC_001_10_more_options_than_the_category_maximum_are_rejected() {
    Api.Response response =
        api.register(
            with(
                external(),
                "optionIds",
                List.of("ws-ai-research", "ws-open-data", "ws-industry-lab")));

    assertFieldError(response, "optionIds", "CATEGORY_LIMIT");
    assertNothingStored();
  }

  @Test
  void AC_001_10_options_up_to_the_category_maximum_are_accepted() {
    assertAccepted(
        api.register(with(external(), "optionIds", List.of("ws-ai-research", "ws-open-data"))));
  }

  @Test
  void AC_001_12_registration_without_the_mandatory_consent_is_rejected() {
    assertFieldError(
        api.register(with(external(), "consentIds", List.of())), "consentIds", "CONSENT_MISSING");
    assertNothingStored();
  }

  @Test
  void AC_001_13_missing_anti_automation_token_is_rejected() {
    assertError(api.register(without(external(), "antiAutomationToken")), 400, "CAPTCHA_FAILED");
    assertNothingStored();
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "wrong-token", "test-mode-fail"})
  void AC_001_13_failed_anti_automation_check_is_rejected(String token) {
    assertError(
        api.register(with(external(), "antiAutomationToken", token)), 400, "CAPTCHA_FAILED");
    assertNothingStored();
  }

  @Test
  void AC_001_14_second_registration_with_the_same_email_is_rejected() {
    Map<String, Object> first = external();
    String email = (String) first.get("email");
    assertAccepted(api.register(first));

    Api.Response second =
        api.register(with(external(), "email", "  " + email.toUpperCase(java.util.Locale.ROOT)));

    assertError(second, 409, "DUPLICATE_EMAIL");
    assertThat(second.json().path("message").asString()).containsIgnoringCase("organizer");
    assertThat(db.countRegistrations()).isEqualTo(1);
    assertThat(copies.copies()).hasSize(1);
  }

  @ParameterizedTest
  @ValueSource(strings = {"firstName", "lastName", "organization"})
  void AC_001_16_value_with_a_line_break_is_rejected(String field) {
    assertFieldError(
        api.register(with(external(), field, "Janez\r\nBcc: someone@example.com")),
        field,
        "CONTROL_CHARACTER");
    assertNothingStored();
  }

  @Test
  void AC_001_16_value_with_another_control_character_is_rejected() {
    assertFieldError(
        api.register(with(external(), "organization", "Institute\tof\u0007Testing")),
        "organization",
        "CONTROL_CHARACTER");
    assertNothingStored();
  }
}

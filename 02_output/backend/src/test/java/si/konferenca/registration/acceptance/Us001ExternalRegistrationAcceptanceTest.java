package si.konferenca.registration.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/** US-001 External participant registration, through the REST contract. */
class Us001ExternalRegistrationAcceptanceTest extends AcceptanceTestBase {

  @Test
  void ac_001_01_validExternalRegistrationIsAccepted() {
    Api.Reply reply = Api.register(Stack.app(), external());

    UUID id = assertAccepted(reply);
    assertThat(OffsetDateTime.parse(reply.json().path("acceptedAt").asString())).isNotNull();
    assertThat(registrationRow(id).get("type")).isEqualTo("EXTERNAL");
  }

  static Stream<Arguments> emptyExternalFields() {
    return EXTERNAL_FIELDS.stream()
        .flatMap(field -> Stream.of(Arguments.of(field, ""), Arguments.of(field, "   ")));
  }

  @ParameterizedTest(name = "{0} = \"{1}\"")
  @MethodSource("emptyExternalFields")
  void ac_001_03_emptyOrBlankRequiredFieldIsRejected(String field, String value) {
    Api.Reply reply = Api.register(Stack.app(), with(external(), field, value));

    assertRejected(reply, field, "required");
    assertNothingStoredOrSent();
  }

  @Test
  void ac_001_04_surroundingWhitespaceIsNotStored() {
    Map<String, Object> registration = external();
    registration.put("firstName", "  Ana ");
    registration.put("lastName", " Novak  ");
    registration.put("email", "  ana.novak@example.org ");
    registration.put("organization", "   Podjetje Primer   ");

    UUID id = assertAccepted(Api.register(Stack.app(), registration));

    Map<String, Object> row = registrationRow(id);
    assertThat(row.get("first_name")).isEqualTo("Ana");
    assertThat(row.get("last_name")).isEqualTo("Novak");
    assertThat(row.get("email")).isEqualTo("ana.novak@example.org");
    assertThat(row.get("organization")).isEqualTo("Podjetje Primer");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "ana.novak",
        "ana@",
        "@example.org",
        "ana novak@example.org",
        "ana@example",
        "ana@@example.org"
      })
  void ac_001_05_invalidEmailIsRejected(String email) {
    Api.Reply reply = Api.register(Stack.app(), with(external(), "email", email));

    assertRejected(reply, "email", "invalid_format");
    assertNothingStoredOrSent();
  }

  @Test
  void ac_001_06_slovenianCharactersAreKeptUnchanged() {
    Map<String, Object> registration = external();
    registration.put("firstName", "Živa");
    registration.put("lastName", "Čučnik Šušteršič");
    registration.put("organization", "Inštitut za računalništvo Žalec");

    UUID id = assertAccepted(Api.register(Stack.app(), registration));

    Map<String, Object> row = registrationRow(id);
    assertThat(row.get("first_name")).isEqualTo("Živa");
    assertThat(row.get("last_name")).isEqualTo("Čučnik Šušteršič");
    assertThat(row.get("organization")).isEqualTo("Inštitut za računalništvo Žalec");
  }

  @Test
  void ac_001_07_unknownOptionIsRejected() {
    Api.Reply reply =
        Api.register(
            Stack.app(), with(external(), "optionIds", List.of("ws-testing", "no-such-option")));

    assertRejected(reply, "optionIds", "option_not_selectable");
    assertNothingStoredOrSent();
  }

  @Test
  void ac_001_08_inactiveOptionIsRejected() {
    Api.Reply reply =
        Api.register(Stack.app(), with(external(), "optionIds", List.of("ws-legacy")));

    assertRejected(reply, "optionIds", "option_not_selectable");
    assertNothingStoredOrSent();
  }

  @Test
  void ac_001_09_missingConsentIsRejected() {
    Api.Reply reply = Api.register(Stack.app(), with(external(), "consent", false));

    assertRejected(reply, "consent", "consent_required");
    assertNothingStoredOrSent();
  }

  @Test
  void ac_001_10_registrationWithoutOptionsIsAccepted() {
    UUID id = assertAccepted(Api.register(Stack.app(), with(external(), "optionIds", List.of())));

    assertThat(registrationRow(id).get("type")).isEqualTo("EXTERNAL");
    assertThat(optionRows(id)).isEmpty();
  }

  @Test
  void ac_001_11_repeatedOptionIsRejected() {
    Api.Reply reply =
        Api.register(
            Stack.app(), with(external(), "optionIds", List.of("ws-testing", "ws-testing")));

    assertRejected(reply, "optionIds", "option_duplicate");
    assertNothingStoredOrSent();
  }

  @Test
  void ac_001_12_failedAntiAutomationCheckIsRejected() {
    Api.Reply reply =
        Api.register(Stack.app(), with(external(), "captchaToken", "not-the-passing-token"));

    assertRejected(reply, "captchaToken", "captcha_failed");
    assertNothingStoredOrSent();
  }

  @Test
  void ac_001_12_missingAntiAutomationCheckIsRejected() {
    Map<String, Object> registration = external();
    registration.remove("captchaToken");

    Api.Reply reply = Api.register(Stack.app(), registration);

    assertThat(reply.status()).isEqualTo(400);
    assertThat(reply.errorFields()).contains("captchaToken");
    assertNothingStoredOrSent();
  }

  @Test
  void ac_001_12_emptyAntiAutomationTokenIsRejected() {
    Api.Reply reply = Api.register(Stack.app(), with(external(), "captchaToken", ""));

    assertThat(reply.status()).isEqualTo(400);
    assertThat(reply.errorFields()).contains("captchaToken");
    assertNothingStoredOrSent();
  }

  @Test
  void ac_001_13_secondRegistrationWithSameEmailIsSeparate() {
    UUID first = assertAccepted(Api.register(Stack.app(), external()));
    UUID second = assertAccepted(Api.register(Stack.app(), with(external(), "firstName", "Anja")));

    assertThat(second).isNotEqualTo(first);
    assertThat(Stack.rows("SELECT id FROM registration WHERE email = ?", "ana.novak@example.org"))
        .hasSize(2);
  }
}

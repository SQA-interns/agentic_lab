package si.konferenca.registration.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** US-001 — External participant registration. */
class ExternalRegistrationAcceptanceTest extends AcceptanceTestBase {

  @Test
  void ac_001_01_validExternalRegistrationIsAccepted() {
    String email = uniqueEmail("ac00101");
    Resp r = register(validExternal(email));

    assertThat(r.status()).as(r.text()).isEqualTo(201);
    JsonNode body = r.json();
    assertThat(body.path("registrationId").asText()).matches("[0-9a-f-]{36}");
    assertThat(body.path("type").asText()).isEqualTo("EXTERNAL");
    assertThat(body.path("firstName").asText()).isEqualTo("Ana");
    assertThat(body.path("lastName").asText()).isEqualTo("Novak");
    assertThat(body.path("email").asText()).isEqualTo(email);
    assertThat(body.path("organization").asText()).isEqualTo("Institut Jožef Stefan");
    assertThat(body.path("submittedAt").asText()).isNotBlank();

    Map<String, String> row = registrationRow(body.path("registrationId").asText());
    assertThat(row.get("type")).isEqualTo("EXTERNAL");
    assertThat(row.get("organization")).isEqualTo("Institut Jožef Stefan");
    assertThat(row.get("email")).isEqualTo(email);
  }

  @ParameterizedTest
  @ValueSource(strings = {"firstName", "lastName", "email", "organization"})
  void ac_001_02_missingRequiredFieldIsRejected(String field) {
    String email = uniqueEmail("ac00102");
    ObjectNode body = validExternal(email);
    body.remove(field);

    Resp r = register(body);

    assertRejectedWithFieldError(r, field, "REQUIRED");
    assertThat(countRegistrationsByEmail(email)).isZero();
  }

  @ParameterizedTest
  @ValueSource(strings = {"firstName", "lastName", "organization"})
  void ac_001_03_whitespaceOnlyValueCountsAsEmpty(String field) {
    String email = uniqueEmail("ac00103");
    ObjectNode body = validExternal(email);
    body.put(field, " \t  ");

    Resp r = register(body);

    assertRejectedWithFieldError(r, field, "REQUIRED");
    assertThat(countRegistrationsByEmail(email)).isZero();
  }

  @Test
  void ac_001_03_whitespaceOnlyEmailCountsAsEmpty() {
    ObjectNode body = validExternal("ignored@example.si");
    body.put("email", "   ");

    assertRejectedWithFieldError(register(body), "email", "REQUIRED");
  }

  @ParameterizedTest
  @ValueSource(strings = {"ana.novak", "ana@", "@example.com", "ana novak@example.si", "ana@@x.si"})
  void ac_001_04_invalidEmailIsRejected(String invalid) {
    ObjectNode body = validExternal("placeholder@example.si");
    body.put("email", invalid);

    Resp r = register(body);

    assertRejectedWithFieldError(r, "email", "INVALID_EMAIL");
    assertThat(countRegistrationsByEmail(invalid)).isZero();
  }

  @Test
  void ac_001_05_leadingAndTrailingWhitespaceIsNotSignificant() {
    String email = uniqueEmail("ac00105");
    ObjectNode body = validExternal("  " + email + "  ");
    body.put("firstName", "  Ana  ");
    body.put("lastName", "\tNovak ");
    body.put("organization", "  Institut Jožef Stefan ");

    Resp r = register(body);

    assertThat(r.status()).as(r.text()).isEqualTo(201);
    assertThat(r.json().path("firstName").asText()).isEqualTo("Ana");
    Map<String, String> row = registrationRow(r.json().path("registrationId").asText());
    assertThat(row.get("first_name")).isEqualTo("Ana");
    assertThat(row.get("last_name")).isEqualTo("Novak");
    assertThat(row.get("email")).isEqualTo(email);
    assertThat(row.get("organization")).isEqualTo("Institut Jožef Stefan");
  }

  @Test
  void ac_001_06_slovenianAndUnicodeCharactersArePreserved() {
    String email = uniqueEmail("ac00106");
    ObjectNode body = validExternal(email);
    body.put("firstName", "Živa");
    body.put("lastName", "Čepič");
    body.put("organization", "Univerza v Ljubljani — Fakulteta za računalništvo");

    Resp r = register(body);

    assertThat(r.status()).as(r.text()).isEqualTo(201);
    assertThat(r.json().path("firstName").asText()).isEqualTo("Živa");
    assertThat(r.json().path("lastName").asText()).isEqualTo("Čepič");
    Map<String, String> row = registrationRow(r.json().path("registrationId").asText());
    assertThat(row.get("first_name")).isEqualTo("Živa");
    assertThat(row.get("last_name")).isEqualTo("Čepič");
    assertThat(row.get("organization"))
        .isEqualTo("Univerza v Ljubljani — Fakulteta za računalništvo");
  }

  @Test
  void ac_001_07_missingConsentIsRejected() {
    String email = uniqueEmail("ac00107a");
    ObjectNode body = validExternal(email);
    body.remove("personalDataConsent");

    assertRejectedWithFieldError(register(body), "personalDataConsent", "CONSENT_REQUIRED");
    assertThat(countRegistrationsByEmail(email)).isZero();
  }

  @Test
  void ac_001_07_declinedConsentIsRejected() {
    String email = uniqueEmail("ac00107b");
    ObjectNode body = validExternal(email);
    body.put("personalDataConsent", false);

    assertRejectedWithFieldError(register(body), "personalDataConsent", "CONSENT_REQUIRED");
    assertThat(countRegistrationsByEmail(email)).isZero();
  }

  // AC-001-08 (consent not preselected) is a UI behaviour: frontend/e2e/registration.spec.ts

  @Test
  void ac_001_09_onlyActiveOptionsAreOffered() {
    Resp r = get("/api/options");

    assertThat(r.status()).as(r.text()).isEqualTo(200);
    List<String> ids = new ArrayList<>();
    for (JsonNode o : r.json()) {
      ids.add(o.path("id").asText());
      assertThat(o.path("name").asText()).isNotBlank();
      assertThat(o.path("category").asText()).isIn("WORKSHOP", "EVENT", "MEAL", "OTHER");
    }
    assertThat(ids).containsExactly("ws-ai", "ws-sec", "ev-dinner", "meal-lunch1", "other-tour");
    assertThat(ids).doesNotContain("ws-old", "meal-veg");
  }

  @Test
  void ac_001_10_selectedActiveOptionsAreRecorded() {
    String email = uniqueEmail("ac00110");
    Resp r = register(validExternal(email, "ws-ai", "meal-lunch1", "other-tour"));

    assertThat(r.status()).as(r.text()).isEqualTo(201);
    List<String> returned = new ArrayList<>();
    for (JsonNode o : r.json().path("options")) {
      returned.add(o.path("id").asText());
    }
    assertThat(returned).containsExactlyInAnyOrder("ws-ai", "meal-lunch1", "other-tour");
    assertThat(optionIdsOf(r.json().path("registrationId").asText()))
        .containsExactly("meal-lunch1", "other-tour", "ws-ai");
  }

  @Test
  void ac_001_11_registrationWithoutOptionsIsAccepted() {
    String email = uniqueEmail("ac00111");
    ObjectNode body = validExternal(email);
    body.remove("optionIds");

    Resp r = register(body);

    assertThat(r.status()).as(r.text()).isEqualTo(201);
    assertThat(r.json().path("options").isArray()).isTrue();
    assertThat(r.json().path("options")).isEmpty();
    assertThat(optionIdsOf(r.json().path("registrationId").asText())).isEmpty();
  }

  @Test
  void ac_001_12_unknownOptionIsRejected() {
    String email = uniqueEmail("ac00112");
    Resp r = register(validExternal(email, "ws-ai", "does-not-exist"));

    assertRejectedWithFieldError(r, "optionIds[1]", "UNKNOWN_OPTION");
    assertThat(countRegistrationsByEmail(email)).isZero();
  }

  @Test
  void ac_001_13_inactiveOptionIsRejected() {
    String email = uniqueEmail("ac00113");
    Resp r = register(validExternal(email, "ws-old"));

    assertRejectedWithFieldError(r, "optionIds[0]", "INACTIVE_OPTION");
    assertThat(countRegistrationsByEmail(email)).isZero();
  }

  @Test
  void ac_001_13_optionDeactivatedAfterFormLoadIsRejected() {
    assertThat(get("/api/options").text()).contains("ws-sec");
    writeOptionsFile(
        withChanged(
            defaultOptions(), new Opt("ws-sec", "WORKSHOP", "Varnost spletnih aplikacij", false)));
    String email = uniqueEmail("ac00113b");

    Resp r = register(validExternal(email, "ws-sec"));

    assertRejectedWithFieldError(r, "optionIds[0]", "INACTIVE_OPTION");
    assertThat(countRegistrationsByEmail(email)).isZero();
  }
}

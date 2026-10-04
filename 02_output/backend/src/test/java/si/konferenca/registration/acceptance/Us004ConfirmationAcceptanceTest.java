package si.konferenca.registration.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import si.konferenca.registration.acceptance.support.AcceptanceTestBase;
import si.konferenca.registration.acceptance.support.ApiClient;
import si.konferenca.registration.acceptance.support.Registrations;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/** US-004 Registration confirmation (backend side; the page is covered end to end). */
class Us004ConfirmationAcceptanceTest extends AcceptanceTestBase {

  @Test
  @DisplayName("AC-004-01 an accepted registration is confirmed with its id, type and time")
  void ac004_01_acceptedRegistrationIsConfirmed() {
    Instant before = Instant.now().minusSeconds(5);
    ApiClient.Response response = api.register(Registrations.external());

    assertThat(response.status()).as(response.text()).isEqualTo(201);
    assertThat(response.contentType()).startsWith("application/json");
    JsonNode body = response.json();
    assertThat(idOf(body)).isNotNull();
    assertThat(body.get("type").asString()).isEqualTo("EXTERNAL");
    Instant acceptedAt = Instant.parse(body.get("acceptedAt").asString());
    assertThat(acceptedAt).isBetween(before, Instant.now().plus(Duration.ofSeconds(5)));
  }

  @Test
  @DisplayName("AC-004-02 a rejected registration gets no confirmation but the reasons")
  void ac004_02_rejectedRegistrationGetsReasonsNotConfirmation() {
    ObjectNode registration = Registrations.external();
    registration.put("firstName", " ");
    registration.put("email", "not-an-email");
    registration.put("consentGiven", false);
    ApiClient.Response response = api.register(registration);

    assertThat(response.status()).as(response.text()).isEqualTo(400);
    JsonNode body = response.json();
    assertThat(body.has("id")).isFalse();
    assertThat(body.has("acceptedAt")).isFalse();
    assertFieldError(response, "firstName", "REQUIRED");
    assertFieldError(response, "email", "INVALID_EMAIL");
    assertFieldError(response, "consentGiven", "CONSENT_REQUIRED");
  }

  @Test
  @DisplayName("AC-004-03 when storage fails no confirmation is given and nothing is kept")
  void ac004_03_storageFailureGivesNoConfirmation() {
    ObjectNode registration = Registrations.external();
    String email = Registrations.email(registration);
    ApiClient.Response response;
    jsonCopies.breakStorage();
    try {
      response = api.register(registration);
    } finally {
      jsonCopies.restoreStorage();
    }

    assertThat(response.status()).as(response.text()).isEqualTo(503);
    assertThat(response.contentType()).startsWith("application/problem+json");
    JsonNode body = response.json();
    assertThat(body.get("code").asString()).isEqualTo("STORAGE_UNAVAILABLE");
    assertThat(body.has("id")).isFalse();
    assertThat(response.text())
        .doesNotContain("Exception")
        .doesNotContain("java.")
        .doesNotContain("at si.")
        .doesNotContain(jsonCopies.dir().getFileName().toString())
        .doesNotContainIgnoringCase("sql");
    assertNothingStoredFor(email);
  }
}

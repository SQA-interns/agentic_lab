package si.konferenca.registration.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static si.konferenca.registration.acceptance.support.Registrations.OPTION_MEAL_NAME;
import static si.konferenca.registration.acceptance.support.Registrations.OPTION_WORKSHOP_NAME;
import static si.konferenca.registration.acceptance.support.Registrations.external;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import si.konferenca.registration.acceptance.support.AcceptanceTestBase;
import si.konferenca.registration.acceptance.support.Api;
import si.konferenca.registration.acceptance.support.Database;
import si.konferenca.registration.acceptance.support.Problems;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * US-004 Registration confirmation: the API answers with the confirmation only after acceptance.
 * What the page shows is covered by the frontend acceptance tests.
 */
class Us004ConfirmationAcceptanceTest extends AcceptanceTestBase {

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    configure(registry, Map.of());
  }

  @Test
  void ac004_01_acceptedRegistrationIsAnsweredWithTheConfirmation() {
    Api.Response response = api.register(external());

    assertThat(response.status()).as("body: %s", response.text()).isEqualTo(201);
    JsonNode body = response.json();
    assertThat(UUID.fromString(body.path("registrationId").asString())).isNotNull();
    assertThat(body.path("firstName").asString()).isEqualTo("Ana");
    assertThat(body.path("lastName").asString()).isEqualTo("Novak");
    assertThat(body.path("email").asString()).isEqualTo("ana.novak@example.com");
    assertThat(body.path("options").findValuesAsString("name"))
        .containsExactlyInAnyOrder(OPTION_WORKSHOP_NAME, OPTION_MEAL_NAME);
    assertThat(Instant.parse(body.path("receivedAt").asString()))
        .isBefore(Instant.now().plusSeconds(5));
    assertThat(Database.registrationCount()).isEqualTo(1);
  }

  @Test
  void ac004_02_rejectedRegistrationIsAnsweredWithoutConfirmation() {
    ObjectNode request = external();
    request.put("lastName", "");

    Api.Response response = api.register(request);

    assertThat(response.status()).isEqualTo(400);
    assertThat(response.contentType()).startsWith("application/problem+json");
    assertThat(response.json().has("registrationId")).isFalse();
    assertNothingStored();
  }

  @Test
  void ac004_03_storageFailureIsAnsweredWithAnErrorWithoutInternalDetails() {
    Database.failOnCommit();

    Api.Response response = api.register(external());

    assertThat(response.status()).as("body: %s", response.text()).isEqualTo(500);
    assertThat(response.json().has("registrationId")).isFalse();
    Problems.assertNoInternals(response);
    Database.removeFaults();
    assertNothingStored();
  }
}

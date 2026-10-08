package si.konferenca.registration.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static si.konferenca.registration.acceptance.support.Registrations.external;
import static si.konferenca.registration.acceptance.support.Registrations.with;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import si.konferenca.registration.acceptance.support.AcceptanceStack;
import si.konferenca.registration.acceptance.support.Api;
import si.konferenca.registration.acceptance.support.Response;
import si.konferenca.registration.acceptance.support.Storage;

/**
 * US-004 Registration confirmation, API side: the confirmation data is returned only for an
 * accepted registration. The visible confirmation is covered by the frontend acceptance tests.
 */
class Us004ConfirmationAcceptanceTest {

  private final Api api = AcceptanceStack.shared().api();

  @Test
  @DisplayName("AC-004-01 an accepted registration returns its confirmation data")
  void ac004_01_acceptedRegistrationReturnsConfirmation() {
    Response response = api.register(external());

    assertThat(response.status()).as(response.toString()).isEqualTo(201);
    assertThat(response.contentType()).startsWith("application/json");
    UUID id = UUID.fromString(response.json().path("id").asString());
    Instant receivedAt = Instant.parse(response.json().path("receivedAt").asString());
    assertThat(receivedAt).isBefore(Instant.now().plusSeconds(5));
    assertThat(AcceptanceStack.db().registrationById(id)).isPresent();
  }

  @Test
  @DisplayName("AC-004-02 a rejected registration returns a problem without internal details")
  void ac004_02_rejectedRegistrationReturnsProblemWithoutInternals() {
    Storage.Snapshot before = Storage.snapshot();

    Response invalid = api.register(with(external(), "email", "not-an-email"));
    Response malformed =
        api.registerRaw("{\"type\": \"EXTERNAL\", \"firstName\": ", "application/json");

    for (Response response : new Response[] {invalid, malformed}) {
      assertThat(response.status()).as(response.toString()).isEqualTo(400);
      assertThat(response.contentType()).startsWith("application/problem+json");
      assertThat(response.json().has("id")).isFalse();
      assertThat(response.text())
          .doesNotContain("Exception")
          .doesNotContain("java.")
          .doesNotContain("org.springframework")
          .doesNotContain("\tat ")
          .doesNotContainIgnoringCase("sql");
    }
    before.assertUnchanged();
  }
}

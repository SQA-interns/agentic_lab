package si.konferenca.registration.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static si.konferenca.registration.acceptance.support.Registrations.external;
import static si.konferenca.registration.acceptance.support.Registrations.student;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import si.konferenca.registration.acceptance.support.AcceptanceTestBase;
import si.konferenca.registration.acceptance.support.Api;
import si.konferenca.registration.acceptance.support.Database;
import si.konferenca.registration.acceptance.support.JsonCopies;
import si.konferenca.registration.acceptance.support.TestEnvironment;

/**
 * US-006 / US-007 with an unreachable SMTP server (D-07): the registration stays accepted and the
 * confirmation is still returned.
 */
class Us006Us007EmailFailureAcceptanceTest extends AcceptanceTestBase {

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    configure(
        registry,
        Map.of(
            "SMTP_HOST", "localhost", "SMTP_PORT", String.valueOf(TestEnvironment.closedPort())));
  }

  private void assertAcceptedDespiteMailFailure(Api.Response response) {
    assertThat(response.status()).as("body: %s", response.text()).isEqualTo(201);
    String id = response.json().path("registrationId").asString();
    assertThat(Database.rows("SELECT id FROM registration WHERE id = ?::uuid", id)).hasSize(1);
    assertThat(JsonCopies.files()).containsExactly(JsonCopies.fileFor(id));
  }

  @Test
  void ac006_03_participantEmailFailureKeepsTheRegistrationAccepted() throws Exception {
    Api.Response response = api.register(external());

    assertAcceptedDespiteMailFailure(response);
    Thread.sleep(3000);
    assertThat(Database.registrationCount()).as("still stored after sending failed").isEqualTo(1);
  }

  @Test
  void ac007_04_organizerEmailFailureKeepsTheRegistrationAccepted() throws Exception {
    Api.Response response = api.register(student());

    assertAcceptedDespiteMailFailure(response);
    Thread.sleep(3000);
    assertThat(Database.registrationCount()).as("still stored after sending failed").isEqualTo(1);
    assertThat(JsonCopies.files()).hasSize(1);
  }
}

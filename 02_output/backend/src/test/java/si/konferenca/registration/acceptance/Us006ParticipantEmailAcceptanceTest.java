package si.konferenca.registration.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static si.konferenca.registration.acceptance.support.Registrations.OPTION_MEAL_NAME;
import static si.konferenca.registration.acceptance.support.Registrations.OPTION_WORKSHOP_NAME;
import static si.konferenca.registration.acceptance.support.Registrations.external;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import si.konferenca.registration.acceptance.support.AcceptanceTestBase;
import si.konferenca.registration.acceptance.support.Api;
import si.konferenca.registration.acceptance.support.Mailpit;
import si.konferenca.registration.acceptance.support.TestEnvironment;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/** US-006 Participant email confirmation, read from the mail catcher. */
class Us006ParticipantEmailAcceptanceTest extends AcceptanceTestBase {

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    configure(registry, Map.of());
  }

  @Test
  void ac006_01_participantReceivesOneConfirmationWithNameAndOptions() {
    Api.Response response = api.register(external());
    assertThat(response.status()).as("body: %s", response.text()).isEqualTo(201);

    Mailpit.awaitMessages(2);
    List<JsonNode> toParticipant = Mailpit.messagesTo("ana.novak@example.com");

    assertThat(toParticipant).hasSize(1);
    JsonNode email = toParticipant.get(0);
    assertThat(email.path("Subject").asString()).contains(TestEnvironment.CONFERENCE_NAME);
    assertThat(email.path("From").path("Address").asString()).isEqualTo(TestEnvironment.MAIL_FROM);
    String text = email.path("Text").asString();
    assertThat(text)
        .contains("Ana Novak")
        .contains(OPTION_WORKSHOP_NAME)
        .contains(OPTION_MEAL_NAME)
        .contains(response.json().path("registrationId").asString());
  }

  @Test
  void ac006_02_noEmailIsSentForARejectedRegistration() {
    ObjectNode request = external();
    request.put("email", "not-an-email");

    assertThat(api.register(request).status()).isEqualTo(400);

    assertThat(Mailpit.messagesAfterQuietPeriod()).isEmpty();
  }

  @Test
  void ac006_04_slovenianCharactersArriveUnchangedInTheParticipantEmail() {
    ObjectNode request = external();
    request.put("firstName", "Žiga");
    request.put("lastName", "Čebašek");
    request.put("email", "ziga.cebasek@example.com");
    assertThat(api.register(request).status()).isEqualTo(201);

    Mailpit.awaitMessages(2);
    List<JsonNode> toParticipant = Mailpit.messagesTo("ziga.cebasek@example.com");

    assertThat(toParticipant).hasSize(1);
    assertThat(toParticipant.get(0).path("Text").asString()).contains("Žiga Čebašek");
  }
}

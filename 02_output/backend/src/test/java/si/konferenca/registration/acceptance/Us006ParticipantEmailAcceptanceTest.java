package si.konferenca.registration.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import si.konferenca.registration.acceptance.support.AcceptanceEnvironment;
import si.konferenca.registration.acceptance.support.AcceptanceTestBase;
import si.konferenca.registration.acceptance.support.ApiClient;
import si.konferenca.registration.acceptance.support.Registrations;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/** US-006 Participant email confirmation. */
class Us006ParticipantEmailAcceptanceTest extends AcceptanceTestBase {

  @Test
  @DisplayName("AC-006-01 the participant gets a confirmation email with details and options")
  void ac006_01_participantGetsConfirmationEmail() {
    ObjectNode registration =
        Registrations.withOptions(Registrations.external(), "ws-security", "meal-dinner");
    registration.put("firstName", "Mojca");
    registration.put("lastName", "Šušteršič");
    String email = Registrations.email(registration);
    registerAccepted(registration);

    List<JsonNode> messages = mailpit.awaitMessagesTo(email, 1, MAIL_WAIT);
    assertThat(messages).hasSize(1);
    JsonNode message = messages.get(0);
    assertThat(message.get("From").get("Address").asString())
        .isEqualTo(AcceptanceEnvironment.MAIL_FROM);
    assertThat(message.get("To")).hasSize(1);
    assertThat(message.get("To").get(0).get("Address").asString()).isEqualTo(email);
    assertThat(message.get("Subject").asString())
        .isEqualTo("Registration confirmed: " + AcceptanceEnvironment.CONFERENCE_NAME);
    assertThat(message.get("Text").asString())
        .contains("Mojca")
        .contains("Šušteršič")
        .contains(email)
        .contains("Univerza v Mariboru")
        .contains("Delavnica: varnost spletnih aplikacij")
        .contains("Večerja za udeležence");
    assertThat(message.get("Attachments")).isEmpty();
  }

  @Test
  @DisplayName("AC-006-02 a rejected registration sends no email to the participant")
  void ac006_02_rejectedRegistrationSendsNoEmail() {
    ObjectNode registration = Registrations.student();
    registration.put("studentId", "");
    ApiClient.Response response = api.register(registration);

    assertThat(response.status()).as(response.text()).isEqualTo(400);
    assertNothingStoredFor(Registrations.email(registration));
  }

  @Test
  @DisplayName("AC-006-03 a failed participant email does not affect the stored registration")
  void ac006_03_emailFailureKeepsRegistration() {
    ObjectNode registration = Registrations.student();
    String email = Registrations.email(registration);
    ApiClient.Response response;
    assertThat(api.formConfig().status()).as("application serves the form").isEqualTo(200);
    mailpit.failAllDeliveries(true);
    try {
      response = api.register(registration);
    } finally {
      mailpit.failAllDeliveries(false);
    }

    assertThat(response.status()).as(response.text()).isEqualTo(201);
    UUID id = idOf(response.json());
    assertThat(database.countByEmail(email)).isEqualTo(1);
    assertThat(jsonCopies.fileFor(id)).isPresent();
  }
}

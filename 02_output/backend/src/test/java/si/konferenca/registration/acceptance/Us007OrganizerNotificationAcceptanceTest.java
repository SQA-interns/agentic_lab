package si.konferenca.registration.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import si.konferenca.registration.acceptance.support.AcceptanceEnvironment;
import si.konferenca.registration.acceptance.support.AcceptanceTestBase;
import si.konferenca.registration.acceptance.support.ApiClient;
import si.konferenca.registration.acceptance.support.Mailpit;
import si.konferenca.registration.acceptance.support.Registrations;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/** US-007 Organizer notification. */
class Us007OrganizerNotificationAcceptanceTest extends AcceptanceTestBase {

  @Test
  @DisplayName("AC-007-01 each organizer gets the data with the raw JSON copy attached")
  void ac007_01_eachOrganizerGetsNotificationWithJsonCopy() {
    ObjectNode registration = Registrations.withOptions(Registrations.student(), "ev-city-tour");
    registration.put("firstName", "Nejc");
    registration.put("lastName", "Žnidaršič");
    String email = Registrations.email(registration);
    UUID id = idOf(registerAccepted(registration));
    byte[] stored = jsonCopies.bytes(jsonCopies.fileFor(id).orElseThrow());

    for (String organizer : AcceptanceEnvironment.ORGANIZER_EMAILS) {
      List<JsonNode> messages = mailpit.awaitMessagesTo(organizer, id.toString(), MAIL_WAIT);
      assertThat(messages).as("notifications to %s", organizer).hasSize(1);
      JsonNode message = messages.get(0);
      assertThat(message.get("From").get("Address").asString())
          .isEqualTo(AcceptanceEnvironment.MAIL_FROM);
      assertThat(message.get("To")).hasSize(1);
      assertThat(message.get("To").get(0).get("Address").asString()).isEqualTo(organizer);
      assertThat(message.get("Subject").asString())
          .isEqualTo("New registration (student): " + AcceptanceEnvironment.CONFERENCE_NAME);
      assertThat(message.get("Text").asString())
          .contains("Nejc")
          .contains("Žnidaršič")
          .contains(email)
          .contains("Univerza v Ljubljani")
          .contains("Računalništvo in informatika")
          .contains("63210042")
          .contains("Ogled mesta Ljubljana");
      assertThat(message.get("Attachments")).hasSize(1);
      JsonNode attachment = message.get("Attachments").get(0);
      assertThat(attachment.get("FileName").asString()).isEqualTo("registration-" + id + ".json");
      assertThat(attachment.get("ContentType").asString()).startsWith("application/json");
      assertThat(
              mailpit.attachment(message.get("ID").asString(), attachment.get("PartID").asString()))
          .isEqualTo(stored);
    }
  }

  @Test
  @DisplayName("AC-007-02 a rejected registration sends no organizer notification")
  void ac007_02_rejectedRegistrationSendsNoNotification() {
    ObjectNode registration = Registrations.external();
    registration.put("organization", "\t");
    String email = Registrations.email(registration);
    ApiClient.Response response = api.register(registration);

    assertThat(response.status()).as(response.text()).isEqualTo(400);
    Mailpit.settle();
    for (String organizer : AcceptanceEnvironment.ORGANIZER_EMAILS) {
      assertThat(mailpit.messagesTo(organizer, email))
          .as("notifications to %s", organizer)
          .isEmpty();
    }
    assertNothingStoredFor(email);
  }

  @Test
  @DisplayName("AC-007-03 a failed organizer notification does not affect the stored registration")
  void ac007_03_notificationFailureKeepsRegistration() {
    ObjectNode registration = Registrations.external();
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

package si.konferenca.registration.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static si.konferenca.registration.acceptance.support.Registrations.external;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import si.konferenca.registration.acceptance.support.AcceptanceTestBase;
import si.konferenca.registration.acceptance.support.Api;
import si.konferenca.registration.acceptance.support.JsonCopies;
import si.konferenca.registration.acceptance.support.Mailpit;
import si.konferenca.registration.acceptance.support.TestEnvironment;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/** US-007 Organizer notification with the raw JSON copy attached, read from the mail catcher. */
class Us007OrganizerNotificationAcceptanceTest extends AcceptanceTestBase {

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    configure(registry, Map.of());
  }

  private static JsonNode organizerEmail(String organizer) {
    Mailpit.awaitMessages(2);
    List<JsonNode> messages = Mailpit.messagesTo(organizer);
    assertThat(messages).as("emails to %s", organizer).hasSize(1);
    return messages.get(0);
  }

  @Test
  void ac007_01_everyOrganizerReceivesTheSubmittedData() {
    ObjectNode request = external();
    request.put("lastName", "Novak-Horvat");
    Api.Response response = api.register(request);
    assertThat(response.status()).as("body: %s", response.text()).isEqualTo(201);

    for (String organizer : TestEnvironment.ORGANIZER_EMAILS) {
      String text = organizerEmail(organizer).path("Text").asString();
      assertThat(text)
          .contains("Ana")
          .contains("Novak-Horvat")
          .contains("ana.novak@example.com")
          .contains("Institut Jozef Stefan")
          .contains("Delavnica: testiranje")
          .contains(response.json().path("registrationId").asString());
    }
  }

  @Test
  void ac007_02_organizerEmailHasTheStoredJsonCopyAttached() {
    Api.Response response = api.register(external());
    assertThat(response.status()).as("body: %s", response.text()).isEqualTo(201);
    String id = response.json().path("registrationId").asString();

    JsonNode email = organizerEmail(TestEnvironment.ORGANIZER_EMAILS.get(0));
    List<JsonNode> attachments = new ArrayList<>();
    email.path("Attachments").forEach(attachments::add);

    assertThat(attachments).hasSize(1);
    JsonNode attachment = attachments.get(0);
    assertThat(attachment.path("FileName").asString()).isEqualTo("registration-" + id + ".json");
    assertThat(attachment.path("ContentType").asString()).startsWith("application/json");
    byte[] content =
        Mailpit.attachment(email.path("ID").asString(), attachment.path("PartID").asString());
    assertThat(content).isEqualTo(JsonCopies.bytes(id));
  }

  @Test
  void ac007_03_noOrganizerEmailIsSentForARejectedRegistration() {
    ObjectNode request = external();
    request.put("organization", " ");

    assertThat(api.register(request).status()).isEqualTo(400);

    List<JsonNode> messages = Mailpit.messagesAfterQuietPeriod();
    assertThat(messages).isEmpty();
  }
}

package lab.conference.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Map;
import lab.conference.acceptance.support.Api;
import lab.conference.acceptance.support.AppInstance;
import lab.conference.acceptance.support.Infra;
import lab.conference.acceptance.support.Mailpit;
import lab.conference.acceptance.support.Payloads;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;

/** US-006/US-007: SMTP outage does not affect acceptance; retry delivers after recovery. */
class SmtpOutageAcceptanceTest {

  @Test
  void ac_006_02_and_ac_007_02_mailsAreDeliveredAfterSmtpRecovers() throws Exception {
    int smtpPort = Infra.freePort();
    try (AppInstance app = AppInstance.builder().smtp("127.0.0.1", smtpPort).build().start()) {
      Map<String, Object> body = Payloads.external();
      String email = (String) body.get("email");

      Api.Response r = app.api().postExternal(body);

      assertThat(r.status()).as("accepted while SMTP is down: " + r).isEqualTo(201);
      String id = r.json().path("registrationId").asText();
      Mailpit.sleep(3000);
      assertThat(app.store().pendingNotifications()).as("intents kept for retry").isEqualTo(2);

      GenericContainer<?> recovered = Infra.newMailpitWithSmtpOnFixedPort(smtpPort);
      recovered.start();
      try {
        Mailpit catcher = new Mailpit(recovered);
        JsonNode participant =
            catcher.message(
                catcher
                    .awaitMessagesTo(email, 1, Duration.ofMinutes(2))
                    .get(0)
                    .path("ID")
                    .asText());
        assertThat(participant.path("Text").asText()).contains(id);

        JsonNode organizerSummary =
            catcher.awaitMessagesTo(app.organizerEmail(), 1, Duration.ofMinutes(2)).get(0);
        JsonNode organizer = catcher.message(organizerSummary.path("ID").asText());
        assertThat(organizer.path("Subject").asText()).contains(id);
        JsonNode attachment = organizer.path("Attachments").get(0);
        assertThat(attachment.path("FileName").asText()).isEqualTo("registration-" + id + ".json");
        assertThat(catcher.part(organizer.path("ID").asText(), attachment.path("PartID").asText()))
            .isEqualTo(Files.readAllBytes(app.store().jsonFile(id)));

        long deadline = System.currentTimeMillis() + 30_000;
        while (app.store().pendingNotifications() > 0 && System.currentTimeMillis() < deadline) {
          Mailpit.sleep(300);
        }
        assertThat(app.store().pendingNotifications()).isZero();
      } finally {
        recovered.stop();
      }
    }
  }
}

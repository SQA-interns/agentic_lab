package si.konferenca.registration.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;

/** US-007 — Organizer notification (specification §11). */
class OrganizerNotificationAcceptanceTest extends AcceptanceTestBase {

  private static String subjectFor(String id) {
    return "New registration " + id;
  }

  @Test
  void ac_007_01_organizerIsNotifiedWithSubmittedDataAndJsonAttachment() throws IOException {
    String email = uniqueEmail("ac00701");
    ObjectNode body = validStudent(email, "ev-dinner");
    body.put("lastName", "Šuštar");
    Resp r = register(body);
    assertThat(r.status()).as(r.text()).isEqualTo(201);
    String id = r.json().path("registrationId").asText();

    Mailbox.Mail mail = Mailbox.awaitMail(ORGANIZER_EMAIL, m -> m.subject().equals(subjectFor(id)));

    assertThat(mail.subject()).doesNotContain(email).doesNotContain("Šuštar");
    assertThat(mail.text())
        .contains("Luka")
        .contains("Šuštar")
        .contains(email)
        .contains("Student")
        .contains("Fakulteta za računalništvo in informatiko")
        .contains("Računalništvo in informatika")
        .contains("63200001")
        .contains("ev-dinner")
        .contains("Conference dinner");

    assertThat(mail.attachments()).hasSize(1);
    Mailbox.Attachment attachment = mail.attachments().get(0);
    assertThat(attachment.fileName()).isEqualTo("registration-" + id + ".json");
    assertThat(attachment.contentType()).startsWith("application/json");
    JsonNode json = JSON.readTree(attachment.content());
    assertThat(json.path("registrationId").asText()).isEqualTo(id);
    assertThat(json.path("lastName").asText()).isEqualTo("Šuštar");
    assertThat(attachment.content()).isEqualTo(Files.readAllBytes(backupFileOf(id)));
  }

  @Test
  void ac_007_02_noNotificationForRejectedSubmission() {
    String email = uniqueEmail("ac00702");
    ObjectNode body = validExternal(email);
    body.put("email", "not-an-email");
    body.put("lastName", "Ac00702Marker");
    assertThat(register(body).status()).isEqualTo(400);

    assertThat(Mailbox.afterGrace(ORGANIZER_EMAIL, m -> m.text().contains("Ac00702Marker")))
        .isEmpty();
  }
}

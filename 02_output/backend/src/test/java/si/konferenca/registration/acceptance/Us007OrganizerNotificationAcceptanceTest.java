package si.konferenca.registration.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static si.konferenca.registration.acceptance.support.Api.awaitMails;
import static si.konferenca.registration.acceptance.support.Api.exportRowsFor;
import static si.konferenca.registration.acceptance.support.Api.external;
import static si.konferenca.registration.acceptance.support.Api.mailDetail;
import static si.konferenca.registration.acceptance.support.Api.mailPart;
import static si.konferenca.registration.acceptance.support.Api.mailsAfter;
import static si.konferenca.registration.acceptance.support.Api.recipients;
import static si.konferenca.registration.acceptance.support.Api.register;
import static si.konferenca.registration.acceptance.support.Api.student;
import static si.konferenca.registration.acceptance.support.Api.uniqueEmail;

import java.io.IOException;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import si.konferenca.registration.acceptance.support.Api.Response;
import si.konferenca.registration.acceptance.support.Backend;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/** US-007 Organizer notification with the raw JSON attached, observed in Mailpit. */
class Us007OrganizerNotificationAcceptanceTest {

  private static Backend backend;

  @BeforeAll
  static void start() {
    backend = Backend.startDefault();
  }

  @AfterAll
  static void stop() {
    backend.close();
  }

  private static List<JsonNode> organizerMails(String email) {
    return awaitMails(email, 2).stream()
        .filter(
            m ->
                recipients(m).contains(Backend.ORGANIZER_EMAIL_1)
                    || recipients(m).contains(Backend.ORGANIZER_EMAIL_2))
        .toList();
  }

  @Test
  void AC_007_01_everyOrganizerReceivesANotificationWithTheSubmittedData() {
    String email = uniqueEmail();
    assertThat(register(backend, student(email)).status()).isEqualTo(201);

    List<JsonNode> mails = organizerMails(email);

    Set<String> reached = new HashSet<>();
    mails.forEach(m -> reached.addAll(recipients(m)));
    assertThat(reached).contains(Backend.ORGANIZER_EMAIL_1, Backend.ORGANIZER_EMAIL_2);
    JsonNode detail = mailDetail(mails.getFirst());
    assertThat(detail.get("Subject").asString())
        .isEqualTo("New registration (student): " + Backend.CONFERENCE_NAME);
    assertThat(detail.get("Text").asString())
        .contains("Luka", "Kranjc", email, "Univerza v Mariboru", "Informatika", "E1234567")
        .contains("Delavnica: testiranje sistemov UI");
  }

  @Test
  void AC_007_02_notificationHasTheRawJsonAttachedIdenticalToTheStoredCopy() throws IOException {
    String email = uniqueEmail();
    Response response = register(backend, external(email));
    assertThat(response.status()).as(response.text()).isEqualTo(201);
    String id = response.json().get("id").asString();

    JsonNode detail = mailDetail(organizerMails(email).getFirst());

    assertThat(detail.get("Subject").asString())
        .isEqualTo("New registration (external participant): " + Backend.CONFERENCE_NAME);
    JsonNode attachments = detail.get("Attachments");
    assertThat(attachments.size()).isEqualTo(1);
    JsonNode attachment = attachments.get(0);
    assertThat(attachment.get("FileName").asString()).isEqualTo("registration-" + id + ".json");
    assertThat(attachment.get("ContentType").asString()).startsWith("application/json");
    byte[] attached = mailPart(detail, attachment.get("PartID").asString());
    byte[] stored = Files.readAllBytes(backend.jsonCopyDir().resolve(id + ".json"));
    assertThat(attached).isEqualTo(stored);
  }

  @Test
  void AC_007_03_rejectedRegistrationSendsNoOrganizerNotification() {
    String email = uniqueEmail();
    ObjectNode body = student(email);
    body.put("studentId", "");

    Response response = register(backend, body);

    assertThat(response.status()).isEqualTo(400);
    assertThat(mailsAfter(email, 2000)).isEmpty();
  }

  @Test
  void AC_007_04_failingOrganizerNotificationKeepsTheRegistration() throws IOException {
    try (Backend noMail =
        Backend.builder()
            .setting("SMTP_HOST", "127.0.0.1")
            .setting("SMTP_PORT", String.valueOf(Us006ParticipantEmailAcceptanceTest.closedPort()))
            .start()) {
      String email = uniqueEmail();

      Response response = register(noMail, student(email));

      assertThat(response.status()).as(response.text()).isEqualTo(201);
      assertThat(exportRowsFor(noMail, email)).hasSize(1);
    }
  }
}

package lab.conference.acceptance;

import static lab.conference.acceptance.support.Checks.assertNothingStored;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lab.conference.acceptance.support.Api;
import lab.conference.acceptance.support.AppInstance;
import lab.conference.acceptance.support.Mailpit;
import lab.conference.acceptance.support.Payloads;
import lab.conference.acceptance.support.Store;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** US-006 and US-007: participant and organizer emails through the SMTP catcher. */
class NotificationAcceptanceTest {

  private static final Duration MAIL_TIMEOUT = Duration.ofSeconds(30);
  private static AppInstance app;
  private static Mailpit mailpit;

  @BeforeAll
  static void start() {
    app = AppInstance.builder().build().start();
    mailpit = Mailpit.shared();
  }

  @AfterAll
  static void stop() {
    app.stop();
  }

  private static List<String> addresses(JsonNode list) {
    List<String> out = new ArrayList<>();
    list.forEach(a -> out.add(a.path("Address").asText()));
    return out;
  }

  @Test
  void ac_006_01_participantReceivesConfirmationWithIdNameAndActivities() {
    Map<String, Object> body = Payloads.external();
    body.put("firstName", "Žiga");
    body.put("lastName", "Šuštar");
    body.put(
        "selections",
        Payloads.selections(
            List.of("ws-alpha"), List.of("ev-gala"), List.of("meal-veg"), List.of()));
    String email = (String) body.get("email");

    Api.Response r = app.api().postExternal(body);
    assertThat(r.status()).isEqualTo(201);
    String id = r.json().path("registrationId").asText();

    JsonNode summary = mailpit.awaitMessagesTo(email, 1, MAIL_TIMEOUT).get(0);
    JsonNode message = mailpit.message(summary.path("ID").asText());
    assertThat(addresses(message.path("To"))).containsExactly(email);
    String text = message.path("Text").asText();
    assertThat(text)
        .contains(
            id,
            "Žiga",
            "Šuštar",
            "Workshop Alpha – Čebelarstvo",
            "Gala evening",
            "Vegetarian lunch");
  }

  @Test
  void ac_006_01_studentParticipantAlsoReceivesConfirmation() {
    Map<String, Object> body = Payloads.student();
    Api.Response r = app.api().postStudent(body);
    assertThat(r.status()).isEqualTo(201);
    JsonNode summary = mailpit.awaitMessagesTo((String) body.get("email"), 1, MAIL_TIMEOUT).get(0);
    assertThat(mailpit.message(summary.path("ID").asText()).path("Text").asText())
        .contains(r.json().path("registrationId").asText());
  }

  @Test
  void ac_006_03_lineBreaksInSingleLineFieldsAreRejected() {
    for (String field : List.of("firstName", "lastName", "organization", "email")) {
      Map<String, Object> body = Payloads.external();
      String original = (String) body.get(field);
      body.put(field, original + "\r\nBcc: attacker@evil.test");
      Store.Snapshot before = app.store().snapshot();

      Api.Response r = app.api().postExternal(body);

      assertThat(r.status()).as(field + " " + r).isEqualTo(400);
      assertThat(r.json().path("errors").findValuesAsText("field")).contains(field);
      assertNothingStored(app, before, null);
    }
    assertThat(mailpit.messagesTo("attacker@evil.test")).isEmpty();
  }

  @Test
  void ac_006_03_htmlAndHeaderLikeTextIsDeliveredAsPlainTextOnlyToIntendedRecipients() {
    Map<String, Object> body = Payloads.external();
    body.put("firstName", "<b>Bold</b>");
    body.put("organization", "<script>alert('x')</script> & Bcc: attacker@evil.test");
    String email = (String) body.get("email");

    Api.Response r = app.api().postExternal(body);
    assertThat(r.status()).as(r.toString()).isEqualTo(201);
    String id = r.json().path("registrationId").asText();

    JsonNode participant =
        mailpit.message(mailpit.awaitMessagesTo(email, 1, MAIL_TIMEOUT).get(0).path("ID").asText());
    assertThat(addresses(participant.path("To"))).containsExactly(email);
    assertThat(participant.path("Cc").size()).isZero();
    assertThat(participant.path("Bcc").size()).isZero();
    assertThat(participant.path("HTML").asText()).isEmpty();
    assertThat(participant.path("Text").asText()).contains("<b>Bold</b>");
    assertThat(participant.path("Subject").asText()).doesNotContain("<b>", "Bcc");

    JsonNode organizer = organizerMessageFor(id);
    assertThat(organizer.path("HTML").asText()).isEmpty();
    assertThat(organizer.path("Text").asText())
        .contains("<script>alert('x')</script> & Bcc: attacker@evil.test");
    assertThat(addresses(organizer.path("To"))).containsExactly(app.organizerEmail());
    assertThat(organizer.path("Bcc").size()).isZero();
    assertThat(mailpit.messagesTo("attacker@evil.test")).isEmpty();
  }

  @Test
  void ac_007_01_organizerReceivesSubmittedDataAndByteIdenticalJsonAttachment() throws Exception {
    Map<String, Object> body = Payloads.student();
    body.put("firstName", "Nuša");
    Api.Response r = app.api().postStudent(body);
    assertThat(r.status()).isEqualTo(201);
    String id = r.json().path("registrationId").asText();

    JsonNode message = organizerMessageFor(id);
    assertThat(message.path("Text").asText())
        .contains(
            id,
            "Nuša",
            "Kovač",
            (String) body.get("email"),
            "Synthetic University",
            "Computer Science",
            "S-12345/Č",
            "Workshop Alpha – Čebelarstvo",
            "Gala evening");
    JsonNode attachments = message.path("Attachments");
    assertThat(attachments.size()).isEqualTo(1);
    JsonNode attachment = attachments.get(0);
    assertThat(attachment.path("FileName").asText()).isEqualTo("registration-" + id + ".json");
    byte[] attached = mailpit.part(message.path("ID").asText(), attachment.path("PartID").asText());
    assertThat(attached).isEqualTo(Files.readAllBytes(app.store().jsonFile(id)));
  }

  private JsonNode organizerMessageFor(String registrationId) {
    long deadline = System.currentTimeMillis() + MAIL_TIMEOUT.toMillis();
    while (System.currentTimeMillis() < deadline) {
      for (JsonNode m : mailpit.messagesTo(app.organizerEmail())) {
        if (m.path("Subject").asText().contains(registrationId)) {
          return mailpit.message(m.path("ID").asText());
        }
      }
      Mailpit.sleep(300);
    }
    throw new AssertionError("no organizer notification for " + registrationId);
  }
}

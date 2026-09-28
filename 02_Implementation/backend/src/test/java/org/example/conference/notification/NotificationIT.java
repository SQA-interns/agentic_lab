package org.example.conference.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.Address;
import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.example.conference.support.IntegrationTestBase;
import org.example.conference.support.Payloads;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** US-006/US-007, P-08: real SMTP delivery (GreenMail), outage, retry and permanent failure. */
class NotificationIT extends IntegrationTestBase {

  @Autowired OutboxDispatcher dispatcher;
  private GreenMail smtp;

  @BeforeEach
  void startSmtp() {
    smtp = new GreenMail(ServerSetupTest.SMTP);
    smtp.start();
  }

  @AfterEach
  void stopSmtp() {
    smtp.stop();
  }

  private String register() throws Exception {
    String body =
        postJson("/api/registrations/external", Payloads.external())
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);
    return objectMapper.readTree(body).get("registrationId").asText();
  }

  private List<Map<String, Object>> outbox(String id) {
    return jdbc.queryForList(
        "SELECT kind, recipient, status, attempts, last_error FROM email_outbox"
            + " WHERE registration_id = ?::uuid ORDER BY kind, recipient",
        id);
  }

  private static String recipients(MimeMessage message) throws Exception {
    return String.join(
        ",",
        Arrays.stream(message.getAllRecipients()).map(Address::toString).sorted().toList());
  }

  /** AC-006-01, AC-007-01. */
  @Test
  void deliversParticipantConfirmationAndOrganizerNotificationWithJsonAttachment()
      throws Exception {
    String id = register();
    assertThat(dispatcher.dispatchDue()).isEqualTo(3);

    MimeMessage[] messages = smtp.getReceivedMessages();
    assertThat(messages).hasSize(3);
    String file =
        Files.readString(
            BACKUP_DIR.resolve("registrations").resolve(id + ".json"), StandardCharsets.UTF_8);

    MimeMessage participant =
        Arrays.stream(messages)
            .filter(m -> subject(m).startsWith("Registration confirmation"))
            .findFirst()
            .orElseThrow();
    assertThat(recipients(participant)).isEqualTo("spela.synthetic@example.org");
    assertThat(participant.getSubject()).doesNotContain("Špela");
    String participantText = participant.getContent().toString();
    assertThat(participantText).contains("Dear Špela Novak Čebašek").contains(id);

    List<MimeMessage> organizer =
        Arrays.stream(messages).filter(m -> subject(m).startsWith("New registration")).toList();
    assertThat(organizer).hasSize(2);
    for (MimeMessage message : organizer) {
      Multipart multipart = (Multipart) message.getContent();
      String text = null;
      byte[] attachment = null;
      String attachmentName = null;
      for (int i = 0; i < multipart.getCount(); i++) {
        BodyPart part = multipart.getBodyPart(i);
        if (part.getFileName() != null) {
          attachmentName = part.getFileName();
          ByteArrayOutputStream out = new ByteArrayOutputStream();
          part.getInputStream().transferTo(out);
          attachment = out.toByteArray();
        } else {
          text = textOf(part.getContent());
        }
      }
      assertThat(attachmentName).isEqualTo("registration-" + id + ".json");
      assertThat(new String(attachment, StandardCharsets.UTF_8)).isEqualTo(file);
      assertThat(text).contains("Inštitut za žabe").contains("Workshop: Data science in practice");
    }
    assertThat(
            Arrays.stream(messages)
                .flatMap(m -> Arrays.stream(sneakyRecipients(m)))
                .sorted()
                .toList())
        .containsExactly(
            "organizer-a@conference.test",
            "organizer-b@conference.test",
            "spela.synthetic@example.org");
    assertThat(outbox(id)).allSatisfy(r -> assertThat(r.get("status")).isEqualTo("SENT"));
    assertThat(dispatcher.dispatchDue()).isZero();
  }

  /** AC-006-02 / AC-007-02 (P-08): SMTP down keeps acceptance and pending work; recovery sends. */
  @Test
  void smtpOutageKeepsRegistrationAndRetriesAfterRecovery() throws Exception {
    smtp.stop();
    String id = register();
    dispatcher.dispatchDue();
    assertThat(registrationCount()).isEqualTo(1);
    assertThat(outbox(id))
        .hasSize(3)
        .allSatisfy(
            r -> {
              assertThat(r.get("status")).isEqualTo("PENDING");
              assertThat(r.get("attempts")).isEqualTo(1);
              assertThat((String) r.get("last_error")).isNotBlank();
            });

    smtp = new GreenMail(ServerSetupTest.SMTP);
    smtp.start();
    assertThat(dispatcher.dispatchDue()).as("backoff not yet elapsed").isZero();
    jdbc.update("UPDATE email_outbox SET next_attempt_at = now() - interval '1 second'");
    assertThat(dispatcher.dispatchDue()).isEqualTo(3);
    assertThat(smtp.getReceivedMessages()).hasSize(3);
    assertThat(outbox(id)).allSatisfy(r -> assertThat(r.get("status")).isEqualTo("SENT"));
  }

  /** AC-006-02: after max attempts the failure is recorded, never reported as sent. */
  @Test
  void permanentFailureIsRecorded() throws Exception {
    smtp.stop();
    String id = register();
    for (int attempt = 0; attempt < 3; attempt++) {
      jdbc.update("UPDATE email_outbox SET next_attempt_at = now() - interval '1 second'");
      dispatcher.dispatchDue();
    }
    assertThat(outbox(id))
        .allSatisfy(
            r -> {
              assertThat(r.get("status")).isEqualTo("FAILED");
              assertThat(r.get("attempts")).isEqualTo(3);
            });
    assertThat(registrationCount()).isEqualTo(1);
    jdbc.update("UPDATE email_outbox SET next_attempt_at = now() - interval '1 second'");
    assertThat(dispatcher.dispatchDue()).isZero();
  }

  private static String textOf(Object content) throws Exception {
    if (content instanceof Multipart nested) {
      StringBuilder text = new StringBuilder();
      for (int i = 0; i < nested.getCount(); i++) {
        text.append(textOf(nested.getBodyPart(i).getContent()));
      }
      return text.toString();
    }
    return content.toString();
  }

  private static String subject(MimeMessage message) {
    try {
      return message.getSubject();
    } catch (jakarta.mail.MessagingException e) {
      throw new IllegalStateException(e);
    }
  }

  private static String[] sneakyRecipients(MimeMessage message) {
    try {
      return Arrays.stream(message.getAllRecipients())
          .map(Address::toString)
          .toArray(String[]::new);
    } catch (jakarta.mail.MessagingException e) {
      throw new IllegalStateException(e);
    }
  }
}

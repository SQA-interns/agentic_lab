package si.konferenca.registration.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.mail.Address;
import jakarta.mail.Message;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;
import si.konferenca.registration.domain.RegistrationSnapshot;

class MailNotifierTest {

  private final JavaMailSender sender = mock(JavaMailSender.class);
  private final MailNotifier notifier =
      new MailNotifier(sender, "reg@conf.si", List.of("a@conf.si", "b@conf.si"), "Konferenca 2026");

  private MimeMessage captureSent() {
    ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
    verify(sender).send(captor.capture());
    return captor.getValue();
  }

  private void stubMimeMessages() {
    when(sender.createMimeMessage())
        .thenAnswer(inv -> new MimeMessage(Session.getInstance(new Properties())));
  }

  @Test
  void participantConfirmationIsPlainTextToTheParticipant() throws Exception {
    stubMimeMessages();
    UUID id = UUID.randomUUID();

    notifier.sendParticipantConfirmation(JsonBackupStoreTest.snapshot(id));

    MimeMessage sent = captureSent();
    assertThat(Arrays.stream(sent.getRecipients(Message.RecipientType.TO)).map(Address::toString))
        .containsExactly("ziva@x.si");
    assertThat(sent.getSubject()).isEqualTo("Registration confirmation");
    sent.saveChanges();
    assertThat(sent.getContentType()).startsWith("text/plain").contains("UTF-8");
    String text = (String) sent.getContent();
    assertThat(text)
        .contains("Dear Živa Čepič")
        .contains("Konferenca 2026")
        .contains(id.toString())
        .contains("Registration type: Student")
        .contains("Workshops:")
        .contains("  - Delavnica");
  }

  @Test
  void organizerNotificationHasAllFieldsAndTheJsonAttachment() throws Exception {
    stubMimeMessages();
    UUID id = UUID.randomUUID();
    byte[] json = "{\"registrationId\":\"x\"}".getBytes(StandardCharsets.UTF_8);

    notifier.sendOrganizerNotification(JsonBackupStoreTest.snapshot(id), json);

    MimeMessage sent = captureSent();
    assertThat(Arrays.stream(sent.getRecipients(Message.RecipientType.TO)).map(Address::toString))
        .containsExactly("a@conf.si", "b@conf.si");
    assertThat(sent.getSubject()).isEqualTo("New registration " + id);
    Multipart root = (Multipart) sent.getContent();
    String body = null;
    Part attachment = null;
    for (int i = 0; i < root.getCount(); i++) {
      Part part = root.getBodyPart(i);
      if (part.getContent() instanceof Multipart nested) {
        body = (String) nested.getBodyPart(0).getContent();
      } else if (Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition())) {
        attachment = part;
      }
    }
    assertThat(body)
        .contains("Study institution: FRI")
        .contains("Student ID: 6320")
        .contains("Delavnica [ws-1]")
        .doesNotContain("Organization / institution:");
    assertThat(attachment).isNotNull();
    assertThat(attachment.getFileName()).isEqualTo("registration-" + id + ".json");
    assertThat(attachment.getInputStream().readAllBytes()).isEqualTo(json);
  }

  @Test
  void participantTextWithoutOptionsSaysSo() {
    UUID id = UUID.randomUUID();
    RegistrationSnapshot s = JsonBackupStoreTest.snapshot(id);
    RegistrationSnapshot none =
        new RegistrationSnapshot(
            1,
            id,
            s.submittedAt(),
            s.type(),
            s.firstName(),
            s.lastName(),
            s.email(),
            null,
            s.studyInstitution(),
            s.studyProgramme(),
            s.studentId(),
            s.personalDataConsentAt(),
            List.of());

    assertThat(notifier.participantText(none)).contains("No optional activities selected.");
  }
}

package si.konferenca.registration.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.mail.Message;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import si.konferenca.registration.domain.Participant;
import si.konferenca.registration.domain.Registration;
import si.konferenca.registration.domain.RegistrationType;

class MailRegistrationNotifierTest {

  private final JavaMailSender sender = mock(JavaMailSender.class);
  private final List<MimeMessage> sent = new ArrayList<>();
  private final MailRegistrationNotifier notifier =
      new MailRegistrationNotifier(
          sender, "from@konf.si", "Konf 2026", List.of("o1@konf.si", "o2@konf.si"));

  @BeforeEach
  void setUp() {
    when(sender.createMimeMessage())
        .thenAnswer(i -> new MimeMessage(Session.getInstance(new Properties())));
    doAnswer(
            i -> {
              MimeMessage message = i.getArgument(0);
              if ("o1@konf.si".equals(message.getAllRecipients()[0].toString())) {
                throw new MailSendException("rejected");
              }
              // a real transport calls saveChanges(), which writes the part headers
              message.saveChanges();
              sent.add(message);
              return null;
            })
        .when(sender)
        .send(any(MimeMessage.class));
  }

  private static Registration student() {
    return new Registration(
        UUID.fromString("0b9a3c4e-6a8f-4f3e-9d43-2a1f6c5e7b10"),
        RegistrationType.STUDENT,
        new Participant("Žiga", "Čeh\r\nBcc: x@evil.si", "z@e.si", null, "UL", "FRI", "63"),
        List.of(),
        "c1",
        "Consent text",
        Instant.parse("2026-10-05T10:00:00Z"));
  }

  @Test
  void sendsParticipantAndRemainingOrganizerMailsWhenOneFails() throws Exception {
    notifier.registrationAccepted(
        student(), "{\"x\":1}".getBytes(java.nio.charset.StandardCharsets.UTF_8));

    assertThat(sent).hasSize(2);
    MimeMessage participant = sent.get(0);
    assertThat(participant.getRecipients(Message.RecipientType.TO)[0].toString())
        .isEqualTo("z@e.si");
    assertThat(participant.getSubject()).isEqualTo("Registration confirmed: Konf 2026");
    assertThat(participant.getHeader("Bcc")).isNull();
    String text = (String) participant.getContent();
    assertThat(text)
        .contains("Dear Žiga")
        .contains("Registration type: Student")
        .contains("Student ID: 63")
        .contains("- none")
        .doesNotContain("Organization");

    assertThat(participant.getFrom()[0].toString()).isEqualTo("from@konf.si");

    MimeMessage organizer = sent.get(1);
    assertThat(organizer.getFrom()[0].toString()).isEqualTo("from@konf.si");
    assertThat(organizer.getRecipients(Message.RecipientType.TO)[0].toString())
        .isEqualTo("o2@konf.si");
    assertThat(organizer.getSubject()).isEqualTo("New registration (student): Konf 2026");
    Multipart parts = (Multipart) organizer.getContent();
    assertThat(bodyText(parts))
        .contains("New registration for Konf 2026")
        .contains("Registration ID: 0b9a3c4e-6a8f-4f3e-9d43-2a1f6c5e7b10")
        .contains("Accepted at (UTC): 2026-10-05T10:00:00Z")
        .contains("Registration type: Student")
        .contains("First name: Žiga")
        .contains("Email: z@e.si")
        .contains("Study institution: UL")
        .contains("Study programme: FRI")
        .contains("Student ID: 63")
        .contains("Options:\n- none")
        .contains("Consent: Consent text")
        .contains("Consent given at (UTC): 2026-10-05T10:00:00Z");
    Part attachment = findAttachment(parts);
    assertThat(attachment.getFileName())
        .isEqualTo("registration-0b9a3c4e-6a8f-4f3e-9d43-2a1f6c5e7b10.json");
    assertThat(attachment.getContentType()).startsWith("application/json");
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    attachment.getInputStream().transferTo(bytes);
    assertThat(bytes.toString(java.nio.charset.StandardCharsets.UTF_8)).isEqualTo("{\"x\":1}");
  }

  @Test
  void externalMailsListOrganizationAndOptions() throws Exception {
    Registration external =
        new Registration(
            UUID.randomUUID(),
            RegistrationType.EXTERNAL,
            new Participant("Ana", "Novak", "ana@e.si", "IJS", null, null, null),
            List.of(
                new si.konferenca.registration.domain.SelectedOption(
                    "ws-a",
                    "Delavnica A",
                    si.konferenca.registration.domain.OptionCategory.WORKSHOP)),
            "c1",
            "Consent text",
            Instant.parse("2026-10-05T10:00:00Z"));

    notifier.registrationAccepted(external, new byte[] {'{', '}'});

    String participant = (String) sent.get(0).getContent();
    assertThat(participant)
        .contains("Registration type: External participant")
        .contains("Last name: Novak")
        .contains("Organization / institution: IJS")
        .contains("- Delavnica A (workshop)")
        .doesNotContain("[ws-a]")
        .doesNotContain("Student ID");
    assertThat(sent.get(1).getSubject())
        .isEqualTo("New registration (external participant): Konf 2026");
    assertThat(bodyText((Multipart) sent.get(1).getContent()))
        .contains("Organization / institution: IJS")
        .contains("- Delavnica A [ws-a] (workshop)");
  }

  private static String bodyText(Multipart multipart) throws Exception {
    for (int i = 0; i < multipart.getCount(); i++) {
      Part part = multipart.getBodyPart(i);
      if (part.getContent() instanceof String text && part.getDisposition() == null) {
        return text;
      }
      if (part.getContent() instanceof Multipart nested) {
        String text = bodyText(nested);
        if (text != null) {
          return text;
        }
      }
    }
    return null;
  }

  private static Part findAttachment(Multipart multipart) throws Exception {
    for (int i = 0; i < multipart.getCount(); i++) {
      Part part = multipart.getBodyPart(i);
      if (Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition())) {
        return part;
      }
      if (part.getContent() instanceof Multipart nested) {
        Part found = findAttachment(nested);
        if (found != null) {
          return found;
        }
      }
    }
    return null;
  }
}

package si.konferenca.registration.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.mail.Address;
import jakarta.mail.BodyPart;
import jakarta.mail.Message;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;
import si.konferenca.registration.domain.Category;
import si.konferenca.registration.domain.GivenConsent;
import si.konferenca.registration.domain.ParticipantDetails;
import si.konferenca.registration.domain.Registration;
import si.konferenca.registration.domain.RegistrationType;
import si.konferenca.registration.domain.SelectedOption;

class SmtpRegistrationNotifierTest {

  private final JavaMailSender sender = mock(JavaMailSender.class);
  private final SmtpRegistrationNotifier notifier =
      new SmtpRegistrationNotifier(
          sender, "from@konferenca.si", "Konferenca", List.of("o1@example.org", "o2@example.org"));

  {
    when(sender.createMimeMessage())
        .thenAnswer(invocation -> new MimeMessage(Session.getInstance(new Properties())));
  }

  private static Registration external(List<SelectedOption> options) {
    Instant at = Instant.parse("2026-10-09T08:15:30.123Z");
    return new Registration(
        UUID.fromString("3f1c2a9e-8d4b-4c6a-9b1e-2f7d5e8a1c40"),
        RegistrationType.EXTERNAL,
        new ParticipantDetails("Ana", "Novak <b>", "ana@example.si", "IJS", null, null, null),
        at,
        options,
        List.of(new GivenConsent("data", "I agree.", at)));
  }

  private MimeMessage sent() {
    ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
    verify(sender).send(captor.capture());
    MimeMessage message = captor.getValue();
    try {
      // JavaMailSenderImpl does this before sending; the mock does not.
      message.saveChanges();
    } catch (jakarta.mail.MessagingException e) {
      throw new IllegalStateException(e);
    }
    return message;
  }

  private static String text(MimeMessage message) throws Exception {
    Object content = message.getContent();
    if (content instanceof String s) {
      return s;
    }
    Multipart multipart = (Multipart) content;
    BodyPart first = multipart.getBodyPart(0);
    Object inner = first.getContent();
    return inner instanceof Multipart m ? (String) m.getBodyPart(0).getContent() : (String) inner;
  }

  @Test
  void participantConfirmationIsPlainTextWithFixedSubject() throws Exception {
    notifier.notifyParticipant(
        external(
            List.of(
                new SelectedOption("ev", "Reception", Category.EVENT),
                new SelectedOption("ws", "AI", Category.WORKSHOP),
                new SelectedOption("ws2", "Security", Category.WORKSHOP))));

    MimeMessage message = sent();
    assertThat(message.getSubject()).isEqualTo("Registration confirmation: Konferenca");
    assertThat(
            Arrays.stream(message.getRecipients(Message.RecipientType.TO)).map(Address::toString))
        .containsExactly("ana@example.si");
    assertThat(message.getFrom()[0].toString()).isEqualTo("from@konferenca.si");
    assertThat(message.getContentType()).startsWith("text/plain").containsIgnoringCase("UTF-8");
    assertThat(text(message))
        .contains("Ana Novak <b>")
        .contains("Registration type: External participant")
        .contains("Registration ID: 3f1c2a9e-8d4b-4c6a-9b1e-2f7d5e8a1c40")
        .contains("Workshops: AI; Security\nEvents: Reception");
  }

  @Test
  void participantWithoutOptionsIsToldSo() throws Exception {
    notifier.notifyParticipant(external(List.of()));

    assertThat(text(sent())).contains("No options selected");
  }

  @Test
  void organizerNotificationGoesToAllOrganizersWithTheCopyAttached() throws Exception {
    byte[] copy = "{\"registrationId\":\"x\",\"name\":\"Žiga\"}".getBytes(StandardCharsets.UTF_8);

    notifier.notifyOrganizers(external(List.of()), copy);

    MimeMessage message = sent();
    assertThat(message.getSubject()).isEqualTo("New registration: Konferenca");
    assertThat(
            Arrays.stream(message.getRecipients(Message.RecipientType.TO)).map(Address::toString))
        .containsExactly("o1@example.org", "o2@example.org");
    assertThat(text(message))
        .contains("Registered at: 2026-10-09T08:15:30.123Z")
        .contains("Organization: IJS")
        .contains("data: given at 2026-10-09T08:15:30.123Z")
        .doesNotContain("Student ID");
    Multipart multipart = (Multipart) message.getContent();
    BodyPart attachment = multipart.getBodyPart(1);
    assertThat(attachment.getFileName())
        .isEqualTo("registration-3f1c2a9e-8d4b-4c6a-9b1e-2f7d5e8a1c40.json");
    assertThat(attachment.getContentType()).startsWith("application/json");
    assertThat(attachment.getInputStream().readAllBytes()).isEqualTo(copy);
  }
}

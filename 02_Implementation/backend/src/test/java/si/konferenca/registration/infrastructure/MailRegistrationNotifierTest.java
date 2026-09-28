package si.konferenca.registration.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.mail.Address;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;
import si.konferenca.registration.config.AppProperties;
import si.konferenca.registration.domain.OptionCategory;
import si.konferenca.registration.domain.RegistrationType;
import si.konferenca.registration.service.RegistrationSnapshot;

class MailRegistrationNotifierTest {

  private final JavaMailSender mailSender = mock(JavaMailSender.class);

  private final RegistrationSnapshot external =
      new RegistrationSnapshot(
          UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"),
          RegistrationType.EXTERNAL,
          Instant.parse("2026-01-02T03:04:05Z"),
          "Ana",
          "Novak",
          "ana@example.si",
          "<b>Institut</b> Jožef Stefan",
          null,
          null,
          null,
          Map.of("privacy", true),
          List.of(new RegistrationSnapshot.Option("ws-1", OptionCategory.WORKSHOP, "Delavnica")));

  @BeforeEach
  void setUp() {
    when(mailSender.createMimeMessage())
        .thenAnswer(invocation -> new MimeMessage(Session.getInstance(new Properties())));
  }

  private static AppProperties properties(List<String> organizerEmails) {
    return new AppProperties(
        new AppProperties.Recaptcha(true, null, null, "http://localhost"),
        new AppProperties.Mail("no-reply@conference.test", organizerEmails),
        new AppProperties.Backup("./build"),
        new AppProperties.Options(null),
        new AppProperties.Organizer("organizer", null),
        new AppProperties.RateLimit(
            new AppProperties.Limit(10, 600), new AppProperties.Limit(30, 600)),
        new AppProperties.Request(16384));
  }

  private MimeMessage sent() {
    ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
    verify(mailSender).send(captor.capture());
    return captor.getValue();
  }

  private static List<String> addresses(Address[] addresses) {
    return Arrays.stream(addresses).map(a -> ((InternetAddress) a).getAddress()).toList();
  }

  @Test
  void sendsPlainTextConfirmationToParticipant() throws Exception { // AC-006-01
    new MailRegistrationNotifier(mailSender, properties(List.of()))
        .sendParticipantConfirmation(external);

    MimeMessage message = sent();
    assertThat(addresses(message.getRecipients(MimeMessage.RecipientType.TO)))
        .containsExactly("ana@example.si");
    assertThat(addresses(message.getFrom())).containsExactly("no-reply@conference.test");
    assertThat(message.getSubject()).isEqualTo("Conference registration confirmed");
    assertThat(message.getContentType()).startsWith("text/plain");
    String body = (String) message.getContent();
    assertThat(body)
        .contains("Dear Ana")
        .contains("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")
        .contains("External participant")
        .contains("Delavnica (WORKSHOP)")
        // plain text: markup is not interpreted, and no HTML body exists
        .contains("<b>Institut</b> Jožef Stefan");
  }

  @Test
  void sendsOrganizerNotificationWithJsonAttachmentToAllOrganizers() throws Exception { // AC-007
    byte[] json = "{\"registrationId\":\"x\"}".getBytes(StandardCharsets.UTF_8);

    new MailRegistrationNotifier(mailSender, properties(List.of(" a@org.test ", "b@org.test", " ")))
        .sendOrganizerNotification(external, json);

    MimeMessage message = sent();
    message.saveChanges(); // as JavaMailSender does before sending
    assertThat(addresses(message.getRecipients(MimeMessage.RecipientType.TO)))
        .containsExactly("a@org.test", "b@org.test");
    assertThat(message.getSubject()).isEqualTo("New conference registration (EXTERNAL)");
    Multipart multipart = (Multipart) message.getContent();
    String text = null;
    Part attachment = null;
    for (int i = 0; i < multipart.getCount(); i++) {
      Part part = multipart.getBodyPart(i);
      if (part.getFileName() != null) {
        attachment = part;
      } else {
        Object content = part.getContent();
        text =
            content instanceof Multipart nested
                ? (String) nested.getBodyPart(0).getContent()
                : (String) content;
      }
    }
    assertThat(text)
        .contains("Ana")
        .contains("Novak")
        .contains("ana@example.si")
        .contains("privacy=yes");
    assertThat(attachment).isNotNull();
    assertThat(attachment.getFileName())
        .isEqualTo("registration-aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee.json");
    assertThat(attachment.getContentType()).startsWith("application/json");
    assertThat(attachment.getInputStream().readAllBytes()).isEqualTo(json);
  }

  @Test
  void skipsOrganizerNotificationWhenNoOrganizerConfigured() {
    new MailRegistrationNotifier(mailSender, properties(List.of()))
        .sendOrganizerNotification(external, new byte[0]);

    verify(mailSender, never()).send(any(MimeMessage.class));
  }
}

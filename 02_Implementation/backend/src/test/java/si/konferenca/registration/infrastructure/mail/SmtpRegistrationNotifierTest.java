package si.konferenca.registration.infrastructure.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.mail.Address;
import jakarta.mail.BodyPart;
import jakarta.mail.Message;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import si.konferenca.registration.TestFixtures;
import si.konferenca.registration.config.AppProperties;
import si.konferenca.registration.domain.Registration;

class SmtpRegistrationNotifierTest {

  private JavaMailSender mailSender;
  private SmtpRegistrationNotifier notifier;
  private Registration registration;

  @BeforeEach
  void setUp() {
    mailSender = mock(JavaMailSender.class);
    when(mailSender.createMimeMessage())
        .thenAnswer(i -> new MimeMessage(Session.getInstance(new Properties())));
    AppProperties base = TestFixtures.properties(TestFixtures.conference());
    AppProperties props =
        new AppProperties(
            base.conference(),
            base.recaptcha(),
            new AppProperties.Mail(
                "from@example.org", List.of("org1@example.org", "org2@example.org")),
            base.backup(),
            base.organizer(),
            base.rateLimit(),
            base.request(),
            base.cors());
    notifier = new SmtpRegistrationNotifier(mailSender, props);
    registration = TestFixtures.externalRegistration();
    ReflectionTestUtils.setField(registration, "id", UUID.randomUUID());
  }

  private List<MimeMessage> sentMessages(int expected) {
    ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
    verify(mailSender, times(expected)).send(captor.capture());
    return captor.getAllValues();
  }

  static String text(Part part) throws Exception {
    Object content = part.getContent();
    if (content instanceof String s) {
      return Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition()) ? "" : s;
    }
    if (content instanceof Multipart multipart) {
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < multipart.getCount(); i++) {
        sb.append(text(multipart.getBodyPart(i)));
      }
      return sb.toString();
    }
    return "";
  }

  static List<BodyPart> attachments(Part part) throws Exception {
    List<BodyPart> result = new ArrayList<>();
    if (part.getContent() instanceof Multipart multipart) {
      for (int i = 0; i < multipart.getCount(); i++) {
        BodyPart child = multipart.getBodyPart(i);
        if (Part.ATTACHMENT.equalsIgnoreCase(child.getDisposition())) {
          result.add(child);
        } else {
          result.addAll(attachments(child));
        }
      }
    }
    return result;
  }

  static String attachmentContent(BodyPart part) throws Exception {
    Object content = part.getContent();
    if (content instanceof String s) {
      return s;
    }
    try (InputStream in = (InputStream) content) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  @Test
  void sendsParticipantConfirmationWithRegistrationDetails() throws Exception {
    notifier.registrationAccepted(registration, "{\"a\":1}");
    MimeMessage participant = sentMessages(2).getFirst();

    assertThat(participant.getRecipients(Message.RecipientType.TO))
        .extracting(Address::toString)
        .containsExactly("ziga@example.si");
    assertThat(participant.getSubject()).isEqualTo("Conference registration confirmation");
    assertThat(text(participant))
        .contains("Dear Žiga Šušteršič")
        .contains("Registration type: External participant")
        .contains("Workshops: Workshop A")
        .contains("Meals: Lunch")
        .contains("Organization / institution: Univerza v Ljubljani — FRI");
  }

  @Test
  void sendsOrganizerNotificationWithDataAndJsonAttachment() throws Exception {
    notifier.registrationAccepted(registration, "{\"lastName\":\"Šušteršič\"}");
    MimeMessage organizer = sentMessages(2).get(1);

    assertThat(organizer.getRecipients(Message.RecipientType.TO))
        .extracting(Address::toString)
        .containsExactly("org1@example.org", "org2@example.org");
    assertThat(organizer.getSubject())
        .isEqualTo("New conference registration (External participant)");
    assertThat(text(organizer))
        .contains("First name: Žiga")
        .contains("Email: ziga@example.si")
        .contains("Consents given: privacy")
        .contains("Registered at (UTC): 2026-03-01T10:15:30Z");
    List<BodyPart> attachments = attachments(organizer);
    assertThat(attachments).hasSize(1);
    assertThat(attachments.getFirst().getFileName())
        .isEqualTo("registration-" + registration.getId() + ".json");
    assertThat(attachmentContent(attachments.getFirst())).isEqualTo("{\"lastName\":\"Šušteršič\"}");
  }

  @Test
  void studentEmailListsStudentFields() throws Exception {
    Registration student = TestFixtures.studentRegistration();
    ReflectionTestUtils.setField(student, "id", UUID.randomUUID());
    notifier.registrationAccepted(student, "{}");
    MimeMessage participant = sentMessages(2).getFirst();
    assertThat(text(participant))
        .contains("Registration type: Student")
        .contains("Study institution: Fakulteta za računalništvo")
        .contains("Student ID: 63210000")
        .contains("Selected options:\n  none");
  }

  @Test
  void failureOfParticipantEmailStillSendsOrganizerEmail() {
    doThrow(new MailSendException("down"))
        .doNothing()
        .when(mailSender)
        .send(any(MimeMessage.class));
    assertThatCode(() -> notifier.registrationAccepted(registration, "{}"))
        .doesNotThrowAnyException();
    verify(mailSender, times(2)).send(any(MimeMessage.class));
  }

  @Test
  void subjectsContainNoUserInput() throws IOException {
    assertThat(RegistrationMailContent.organizerSubject(registration))
        .doesNotContain(registration.getFirstName())
        .doesNotContain(registration.getEmail());
  }
}

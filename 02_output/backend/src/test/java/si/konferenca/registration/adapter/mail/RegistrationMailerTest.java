package si.konferenca.registration.adapter.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.mail.Address;
import jakarta.mail.Message;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import si.konferenca.registration.application.RegistrationAccepted;
import si.konferenca.registration.domain.Fixtures;
import si.konferenca.registration.domain.Registration;
import si.konferenca.registration.domain.RegistrationType;

class RegistrationMailerTest {

  private final JavaMailSender sender = mock(JavaMailSender.class);
  private final RegistrationMailer mailer =
      new RegistrationMailer(sender, "reg@k.si", "Konf", List.of("o1@k.si", "o2@k.si"));

  private List<MimeMessage> sent() {
    ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
    verify(sender, times(2)).send(captor.capture());
    return captor.getAllValues();
  }

  private static List<String> to(MimeMessage message) throws Exception {
    return Arrays.stream(message.getRecipients(Message.RecipientType.TO))
        .map(Address::toString)
        .toList();
  }

  @Test
  void us006_us007_participantAndOrganizersGetTheirMessages() throws Exception {
    when(sender.createMimeMessage())
        .thenAnswer(i -> new MimeMessage(Session.getInstance(new Properties())));
    Registration registration = Fixtures.registration(RegistrationType.STUDENT);

    mailer.onAccepted(new RegistrationAccepted(registration, new byte[] {'{', '}'}));

    MimeMessage participant = sent().get(0);
    assertThat(to(participant)).containsExactly("luka@example.si");
    assertThat(participant.getSubject()).isEqualTo("Registration confirmed: Konf");
    MimeMessage organizer = sent().get(1);
    assertThat(to(organizer)).containsExactly("o1@k.si", "o2@k.si");
    assertThat(organizer.getSubject()).isEqualTo("New registration (student): Konf");
    assertThat(organizer.getContent()).isInstanceOf(Multipart.class);
  }

  @Test
  void sr05_bodyListsTheDataAndKeepsInputOutOfHeaders() {
    Registration registration = Fixtures.registration(RegistrationType.EXTERNAL);

    String details = RegistrationMailer.details(registration);

    assertThat(details)
        .contains("Registration type: External participant")
        .contains("First name: Ana")
        .contains("Organization / institution: IJS")
        .contains("Workshops: Workshop A")
        .contains("Meals: -")
        .contains("- I agree to processing.");
  }

  @Test
  void d16_sendFailureIsSwallowedAndLogged() {
    when(sender.createMimeMessage())
        .thenAnswer(i -> new MimeMessage(Session.getInstance(new Properties())));
    doThrow(new MailSendException("down")).when(sender).send(any(MimeMessage.class));

    mailer.onAccepted(
        new RegistrationAccepted(Fixtures.registration(RegistrationType.EXTERNAL), new byte[0]));

    verify(sender, times(2)).send(any(MimeMessage.class));
  }
}

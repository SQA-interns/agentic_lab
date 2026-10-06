package si.konferenca.registration.infrastructure.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.mail.Address;
import jakarta.mail.Message;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import si.konferenca.registration.application.Fixtures;

class SmtpNotifierTest {

  private final JavaMailSender sender = mock(JavaMailSender.class);

  SmtpNotifierTest() {
    when(sender.createMimeMessage())
        .thenAnswer(i -> new MimeMessage(Session.getInstance(new Properties())));
  }

  @Test
  void sendsParticipantAndOrganizerMessages() throws Exception {
    SmtpNotifier n =
        new SmtpNotifier(sender, Runnable::run, "from@x.si", List.of("o1@x.si", "o2@x.si"), "Konf");

    n.registrationAccepted(Fixtures.registration(), "{\"a\":1}".getBytes());

    ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
    verify(sender, times(2)).send(sent.capture());
    MimeMessage participant = sent.getAllValues().get(0);
    assertThat(participant.getSubject()).isEqualTo("Registration received: Konf");
    assertThat(participant.getRecipients(Message.RecipientType.TO))
        .extracting(Address::toString)
        .containsExactly("Ana@Example.si");
    MimeMessage organizer = sent.getAllValues().get(1);
    assertThat(organizer.getRecipients(Message.RecipientType.TO)).hasSize(2);
    assertThat(organizer.getContent()).isInstanceOf(Multipart.class);
  }

  @Test
  void noOrganizerMessageWithoutRecipients() {
    new SmtpNotifier(sender, Runnable::run, "f@x.si", List.of(), "K")
        .registrationAccepted(Fixtures.registration(), new byte[0]);

    verify(sender, times(1)).send(any(MimeMessage.class));
  }

  @Test
  void sendFailureIsSwallowed() {
    doThrow(new MailSendException("down")).when(sender).send(any(MimeMessage.class));
    SmtpNotifier n = new SmtpNotifier(sender, Runnable::run, "f@x.si", List.of("o@x.si"), "K");

    assertThatCode(() -> n.registrationAccepted(Fixtures.registration(), new byte[0]))
        .doesNotThrowAnyException();
  }

  @Test
  void fullQueueIsSwallowed() {
    SmtpNotifier n =
        new SmtpNotifier(
            sender,
            r -> {
              throw new RejectedExecutionException("full");
            },
            "f@x.si",
            List.of("o@x.si"),
            "K");

    assertThatCode(() -> n.registrationAccepted(Fixtures.registration(), new byte[0]))
        .doesNotThrowAnyException();
    verify(sender, never()).send(any(MimeMessage.class));
  }
}

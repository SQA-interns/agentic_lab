package si.konferenca.registration.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.internet.MimeMessage;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import si.konferenca.registration.application.MailDeliveryException;
import si.konferenca.registration.application.OutgoingMail;

class SmtpMailerTest {

  /** Captures messages instead of connecting to a server. */
  static final class CapturingSender extends JavaMailSenderImpl {
    final List<MimeMessage> sent = new ArrayList<>();
    boolean fail;

    @Override
    public void send(MimeMessage message) {
      if (fail) {
        throw new MailSendException("451 try later");
      }
      sent.add(message);
    }
  }

  @Test
  void sendsPlainTextUtf8WithoutAttachment() throws Exception {
    CapturingSender sender = new CapturingSender();

    new SmtpMailer(sender, "from@konferenca.si")
        .send(new OutgoingMail(List.of("a@x.si"), "Subject – K", "Živjo <b>x</b>", null, null));

    MimeMessage m = sender.sent.get(0);
    m.saveChanges();
    assertThat(m.getFrom()[0].toString()).isEqualTo("from@konferenca.si");
    assertThat(m.getAllRecipients()).hasSize(1);
    assertThat(m.getSubject()).isEqualTo("Subject – K");
    assertThat(m.getContentType()).startsWith("text/plain").containsIgnoringCase("UTF-8");
    assertThat(m.getContent()).isEqualTo("Živjo <b>x</b>");
  }

  @Test
  void attachesTheJsonCopy() throws Exception {
    CapturingSender sender = new CapturingSender();

    new SmtpMailer(sender, "from@konferenca.si")
        .send(
            new OutgoingMail(
                List.of("o1@x.si", "o2@x.si"),
                "S",
                "body",
                "registration-1.json",
                "{}".getBytes()));

    MimeMessage m = sender.sent.get(0);
    m.saveChanges();
    assertThat(m.getAllRecipients()).hasSize(2);
    Multipart mixed = (Multipart) m.getContent();
    List<String> fileNames = new ArrayList<>();
    collect(mixed, fileNames);
    assertThat(fileNames).contains("registration-1.json");
  }

  @Test
  void deliveryFailureBecomesMailDeliveryException() {
    CapturingSender sender = new CapturingSender();
    sender.fail = true;
    SmtpMailer mailer = new SmtpMailer(sender, "from@konferenca.si");
    OutgoingMail mail = new OutgoingMail(List.of("a@x.si"), "S", "b", null, null);

    assertThatThrownBy(() -> mailer.send(mail)).isInstanceOf(MailDeliveryException.class);
  }

  private static void collect(Multipart multipart, List<String> fileNames) throws Exception {
    for (int i = 0; i < multipart.getCount(); i++) {
      BodyPart part = multipart.getBodyPart(i);
      if (part.getContent() instanceof Multipart nested) {
        collect(nested, fileNames);
      } else if (part.getFileName() != null) {
        fileNames.add(part.getFileName());
      }
    }
  }
}

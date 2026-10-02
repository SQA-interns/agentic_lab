package si.konferenca.registration.adapter.out.mail;

import jakarta.activation.DataHandler;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.util.ByteArrayDataSource;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import si.konferenca.registration.domain.MailNotifier;
import si.konferenca.registration.domain.Registration;
import si.konferenca.registration.domain.TextField;

/**
 * Sends the emails over SMTP as plain text. Headers are set only through the mail API, and subjects
 * contain no participant input, so input cannot inject headers or markup (SR-05).
 */
public class SmtpMailNotifier implements MailNotifier {

  private static final String ENCODING = StandardCharsets.UTF_8.name();

  private final JavaMailSender sender;
  private final String from;
  private final List<String> organizerRecipients;
  private final String conferenceName;

  public SmtpMailNotifier(
      JavaMailSender sender, String from, List<String> organizerRecipients, String conferenceName) {
    this.sender = sender;
    this.from = from;
    this.organizerRecipients = List.copyOf(organizerRecipients);
    this.conferenceName = conferenceName;
  }

  @Override
  public void sendParticipantConfirmation(Registration registration) {
    MimeMessage message = sender.createMimeMessage();
    try {
      MimeMessageHelper helper = new MimeMessageHelper(message, false, ENCODING);
      helper.setFrom(from);
      helper.setTo(registration.value(TextField.EMAIL));
      helper.setSubject("Potrditev prijave: " + conferenceName);
      helper.setText(MailTexts.participantConfirmation(registration, conferenceName), false);
    } catch (MessagingException e) {
      throw new IllegalStateException("The participant confirmation could not be composed", e);
    }
    sender.send(message);
  }

  @Override
  public void sendOrganizerNotification(Registration registration, byte[] jsonCopy) {
    MimeMessage message = sender.createMimeMessage();
    try {
      MimeMessageHelper helper =
          new MimeMessageHelper(message, MimeMessageHelper.MULTIPART_MODE_MIXED, ENCODING);
      helper.setFrom(from);
      helper.setTo(organizerRecipients.toArray(String[]::new));
      helper.setSubject("Nova prijava: " + conferenceName + " (" + registration.id() + ")");
      helper.setText(MailTexts.organizerNotification(registration), false);
      MimeBodyPart attachment = new MimeBodyPart();
      attachment.setDataHandler(
          new DataHandler(new ByteArrayDataSource(jsonCopy, "application/json")));
      attachment.setFileName("registration-" + registration.id() + ".json");
      // Base64, so the attachment arrives byte for byte as the stored copy.
      attachment.setHeader("Content-Transfer-Encoding", "base64");
      helper.getRootMimeMultipart().addBodyPart(attachment);
    } catch (MessagingException e) {
      throw new IllegalStateException("The organizer notification could not be composed", e);
    }
    sender.send(message);
  }
}

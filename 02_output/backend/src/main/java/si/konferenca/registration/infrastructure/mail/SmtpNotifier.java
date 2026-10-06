package si.konferenca.registration.infrastructure.mail;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import si.konferenca.registration.application.RegistrationEmails;
import si.konferenca.registration.application.RegistrationEmails.EmailMessage;
import si.konferenca.registration.application.RegistrationPorts.Notifier;
import si.konferenca.registration.domain.Registration;

/**
 * Sends the participant and organizer emails on the mail executor, after the registration was
 * stored. A failure is logged with the registration id only (D-07, ES-07).
 */
public class SmtpNotifier implements Notifier {

  private static final Logger LOG = LoggerFactory.getLogger(SmtpNotifier.class);

  private final JavaMailSender sender;
  private final RegistrationEmails emails;
  private final String from;
  private final Executor executor;

  public SmtpNotifier(
      JavaMailSender sender, RegistrationEmails emails, String from, Executor executor) {
    this.sender = sender;
    this.emails = emails;
    this.from = from;
    this.executor = executor;
  }

  @Override
  public void registrationAccepted(Registration registration, byte[] jsonCopy) {
    byte[] copy = jsonCopy.clone();
    try {
      executor.execute(
          () -> {
            send(registration, "participant", emails.participantConfirmation(registration));
            send(registration, "organizer", emails.organizerNotification(registration, copy));
          });
    } catch (RuntimeException e) {
      LOG.error("Emails of registration {} could not be queued", registration.id(), e);
    }
  }

  private void send(Registration registration, String kind, EmailMessage message) {
    try {
      MimeMessage mime = sender.createMimeMessage();
      MimeMessageHelper helper =
          new MimeMessageHelper(mime, message.attachment() != null, StandardCharsets.UTF_8.name());
      helper.setFrom(new InternetAddress(from, true));
      InternetAddress[] to = new InternetAddress[message.to().size()];
      for (int i = 0; i < to.length; i++) {
        to[i] = new InternetAddress(message.to().get(i), true);
      }
      helper.setTo(to);
      helper.setSubject(message.subject());
      helper.setText(message.text(), false);
      if (message.attachment() != null) {
        helper.addAttachment(
            message.attachment().filename(),
            new ByteArrayResource(message.attachment().content()),
            message.attachment().contentType());
      }
      sender.send(mime);
      LOG.info("Sent {} email of registration {}", kind, registration.id());
    } catch (MessagingException | MailException | IllegalStateException e) {
      LOG.error(
          "Could not send {} email of registration {}: {}",
          kind,
          registration.id(),
          e.getClass().getSimpleName());
    }
  }
}

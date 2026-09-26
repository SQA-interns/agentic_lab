package si.konferenca.registration.infrastructure.mail;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import si.konferenca.registration.application.port.RegistrationNotifier;
import si.konferenca.registration.config.AppProperties;
import si.konferenca.registration.domain.Registration;

/**
 * Sends the participant confirmation and the organizer notification over SMTP. Failures are logged
 * without personal data and do not undo the already stored registration.
 */
@Component
public class SmtpRegistrationNotifier implements RegistrationNotifier {

  private static final Logger LOG = LoggerFactory.getLogger(SmtpRegistrationNotifier.class);

  private final JavaMailSender mailSender;
  private final String from;
  private final List<String> organizerRecipients;

  public SmtpRegistrationNotifier(JavaMailSender mailSender, AppProperties properties) {
    this.mailSender = mailSender;
    this.from = properties.mail().from();
    this.organizerRecipients = properties.mail().organizerRecipients();
  }

  @Override
  public void registrationAccepted(Registration registration, String json) {
    sendParticipantConfirmation(registration);
    sendOrganizerNotification(registration, json);
  }

  private void sendParticipantConfirmation(Registration registration) {
    try {
      MimeMessage message = mailSender.createMimeMessage();
      MimeMessageHelper helper =
          new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
      helper.setFrom(from);
      helper.setTo(registration.getEmail());
      helper.setSubject(RegistrationMailContent.PARTICIPANT_SUBJECT);
      helper.setText(RegistrationMailContent.participantBody(registration), false);
      mailSender.send(message);
    } catch (MessagingException | MailException e) {
      LOG.error(
          "Participant confirmation email for registration {} failed: {}",
          registration.getId(),
          e.getClass().getSimpleName());
    }
  }

  private void sendOrganizerNotification(Registration registration, String json) {
    if (organizerRecipients.isEmpty()) {
      LOG.warn(
          "No organizer recipients configured; notification for {} skipped", registration.getId());
      return;
    }
    try {
      MimeMessage message = mailSender.createMimeMessage();
      MimeMessageHelper helper =
          new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
      helper.setFrom(from);
      helper.setTo(organizerRecipients.toArray(String[]::new));
      helper.setSubject(RegistrationMailContent.organizerSubject(registration));
      helper.setText(RegistrationMailContent.organizerBody(registration), false);
      helper.addAttachment(
          "registration-" + registration.getId() + ".json",
          new ByteArrayResource(json.getBytes(StandardCharsets.UTF_8)),
          "application/json");
      mailSender.send(message);
    } catch (MessagingException | MailException e) {
      LOG.error(
          "Organizer notification email for registration {} failed: {}",
          registration.getId(),
          e.getClass().getSimpleName());
    }
  }
}

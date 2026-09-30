package si.konferenca.registration.infrastructure;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import si.konferenca.registration.application.NotificationSender;
import si.konferenca.registration.application.RegistrationCopy;
import si.konferenca.registration.settings.AppProperties;

/** Sends the registration emails as UTF-8 plain text over SMTP (SR-05). */
@Component
public class SmtpNotificationSender implements NotificationSender {

  private final JavaMailSender mailSender;
  private final AppProperties properties;

  public SmtpNotificationSender(JavaMailSender mailSender, AppProperties properties) {
    this.mailSender = mailSender;
    this.properties = properties;
  }

  @Override
  public void sendParticipantConfirmation(RegistrationCopy registration) {
    MimeMessage message = mailSender.createMimeMessage();
    try {
      MimeMessageHelper helper =
          new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
      helper.setFrom(properties.mail().from());
      helper.setTo(registration.email());
      helper.setSubject(MailTexts.participantSubject(properties.conferenceName()));
      helper.setText(MailTexts.participantBody(registration, properties.conferenceName()), false);
    } catch (MessagingException e) {
      throw new IllegalStateException("Participant email could not be built", e);
    }
    mailSender.send(message);
  }

  @Override
  public void sendOrganizerNotification(RegistrationCopy registration, byte[] jsonCopy) {
    MimeMessage message = mailSender.createMimeMessage();
    try {
      MimeMessageHelper helper =
          new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
      helper.setFrom(properties.mail().from());
      helper.setTo(
          properties.organizer().emails().stream().map(String::strip).toArray(String[]::new));
      helper.setSubject(MailTexts.organizerSubject(registration));
      helper.setText(MailTexts.organizerBody(registration), false);
      helper.addAttachment(
          "registration-" + registration.id() + ".json",
          new ByteArrayResource(jsonCopy),
          "application/json");
    } catch (MessagingException e) {
      throw new IllegalStateException("Organizer email could not be built", e);
    }
    mailSender.send(message);
  }
}

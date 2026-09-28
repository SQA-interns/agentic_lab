package si.konferenca.registration.infrastructure;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import si.konferenca.registration.config.AppProperties;
import si.konferenca.registration.domain.RegistrationType;
import si.konferenca.registration.service.RegistrationNotifier;
import si.konferenca.registration.service.RegistrationSnapshot;

/**
 * Sends plain-text emails: a confirmation to the participant and a notification with the raw JSON
 * attached to the organizers. No user input is placed in headers other than the validated recipient
 * address.
 */
@Component
public class MailRegistrationNotifier implements RegistrationNotifier {

  static final String PARTICIPANT_SUBJECT = "Conference registration confirmed";
  static final String ORGANIZER_SUBJECT_PREFIX = "New conference registration";

  private static final Logger LOG = LoggerFactory.getLogger(MailRegistrationNotifier.class);

  private final JavaMailSender mailSender;
  private final String from;
  private final List<String> organizerEmails;

  public MailRegistrationNotifier(JavaMailSender mailSender, AppProperties properties) {
    this.mailSender = mailSender;
    this.from = properties.mail().from();
    this.organizerEmails =
        properties.mail().organizerEmails().stream()
            .map(String::strip)
            .filter(address -> !address.isEmpty())
            .toList();
  }

  @Override
  public void sendParticipantConfirmation(RegistrationSnapshot registration) {
    String body =
        "Dear "
            + registration.firstName()
            + ",\n\n"
            + "thank you for registering for the conference. Your registration has been"
            + " received successfully.\n\n"
            + RegistrationText.describe(registration)
            + "\nPlease keep this email as a record of your registration.\n";
    send(List.of(registration.email()), PARTICIPANT_SUBJECT, body, null);
  }

  @Override
  public void sendOrganizerNotification(RegistrationSnapshot registration, byte[] json) {
    if (organizerEmails.isEmpty()) {
      LOG.warn(
          "No organizer email configured; notification for registration {} skipped",
          registration.registrationId());
      return;
    }
    String subject =
        ORGANIZER_SUBJECT_PREFIX
            + " ("
            + (registration.registrationType() == RegistrationType.STUDENT ? "STUDENT" : "EXTERNAL")
            + ")";
    String body =
        "A new participant has registered for the conference.\n\n"
            + RegistrationText.describe(registration)
            + "\nThe raw registration JSON is attached.\n";
    send(organizerEmails, subject, body, new Attachment(registration, json));
  }

  private void send(List<String> recipients, String subject, String body, Attachment attachment) {
    MimeMessage message = mailSender.createMimeMessage();
    try {
      MimeMessageHelper helper =
          new MimeMessageHelper(message, attachment != null, StandardCharsets.UTF_8.name());
      helper.setFrom(from);
      helper.setTo(recipients.toArray(String[]::new));
      helper.setSubject(subject);
      helper.setText(body, false);
      if (attachment != null) {
        helper.addAttachment(
            "registration-" + attachment.registration().registrationId() + ".json",
            new ByteArrayResource(attachment.json()),
            "application/json");
      }
    } catch (MessagingException e) {
      throw new MailPreparationException("Email could not be prepared", e);
    }
    mailSender.send(message);
  }

  private record Attachment(RegistrationSnapshot registration, byte[] json) {}
}

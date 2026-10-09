package si.konferenca.registration.adapter.mail;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import si.konferenca.registration.application.RegistrationAccepted;
import si.konferenca.registration.domain.Category;
import si.konferenca.registration.domain.Field;
import si.konferenca.registration.domain.GivenConsent;
import si.konferenca.registration.domain.Registration;

/**
 * Sends the participant confirmation and the organizer notification after the registration is
 * committed (US-006, US-007, docs/02_contracts/emails.schema.json). Plain text only; participant
 * input appears only in the body (SR-05). A failure is logged without personal data and never
 * affects the stored registration (D-16).
 */
public class RegistrationMailer {

  private static final Logger LOG = LoggerFactory.getLogger(RegistrationMailer.class);

  private final JavaMailSender sender;
  private final String from;
  private final String conferenceName;
  private final List<String> organizers;

  public RegistrationMailer(
      JavaMailSender sender, String from, String conferenceName, List<String> organizers) {
    this.sender = sender;
    this.from = from;
    this.conferenceName = conferenceName;
    this.organizers = List.copyOf(organizers);
  }

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onAccepted(RegistrationAccepted event) {
    Registration registration = event.registration();
    send(
        "participant confirmation",
        registration,
        List.of(registration.email()),
        "Registration confirmed: " + conferenceName,
        participantBody(registration),
        null);
    send(
        "organizer notification",
        registration,
        organizers,
        "New registration (" + registration.type().lowerLabel() + "): " + conferenceName,
        organizerBody(registration),
        event.jsonCopy());
  }

  private void send(
      String kind,
      Registration registration,
      List<String> to,
      String subject,
      String body,
      byte[] attachment) {
    try {
      MimeMessage message = sender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(message, attachment != null, "UTF-8");
      helper.setFrom(from);
      helper.setTo(to.toArray(String[]::new));
      helper.setSubject(subject);
      helper.setText(body, false);
      if (attachment != null) {
        helper.addAttachment(
            "registration-" + registration.id() + ".json",
            new ByteArrayResource(attachment),
            "application/json");
      }
      sender.send(message);
    } catch (MessagingException | MailException e) {
      LOG.error(
          "Sending the {} for registration {} failed: {}",
          kind,
          registration.id(),
          e.getClass().getSimpleName());
    }
  }

  private String participantBody(Registration registration) {
    return "Thank you for registering for "
        + conferenceName
        + ". Your registration was received.\n\n"
        + details(registration);
  }

  private String organizerBody(Registration registration) {
    return "A new registration for "
        + conferenceName
        + " was received. The raw registration JSON is attached.\n\n"
        + "Registration ID: "
        + registration.id()
        + "\nRegistered at: "
        + registration.submittedAt()
        + "\n"
        + details(registration);
  }

  static String details(Registration registration) {
    StringBuilder text = new StringBuilder();
    text.append("Registration type: ").append(registration.type().label()).append('\n');
    for (Field field : Field.of(registration.type())) {
      text.append(field.label()).append(": ").append(registration.value(field)).append('\n');
    }
    text.append("\nSelected options:\n");
    for (Category category : Category.values()) {
      List<String> names = registration.optionNames(category);
      text.append(category.heading())
          .append(": ")
          .append(names.isEmpty() ? "-" : String.join("; ", names))
          .append('\n');
    }
    text.append("\nConsents given:\n");
    for (GivenConsent consent : registration.consents()) {
      text.append("- ").append(consent.text()).append('\n');
    }
    return text.toString();
  }
}

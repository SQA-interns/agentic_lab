package si.konferenca.registration.integration;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import si.konferenca.registration.config.AppProperties;
import si.konferenca.registration.domain.Category;
import si.konferenca.registration.domain.GivenConsent;
import si.konferenca.registration.domain.Participant;
import si.konferenca.registration.domain.Registration;
import si.konferenca.registration.domain.RegistrationType;
import si.konferenca.registration.domain.SelectedOption;

/**
 * Sends the messages of `email-messages.json` (US-006, US-007). Bodies are text/plain UTF-8;
 * headers carry no participant input except the strictly parsed recipient (SR-05).
 */
@Component
public class MailNotifier {

  private final JavaMailSender sender;
  private final AppProperties app;

  public MailNotifier(JavaMailSender sender, AppProperties app) {
    this.sender = sender;
    this.app = app;
  }

  /** Participant confirmation; throws when the message cannot be sent. */
  public void sendParticipantConfirmation(Registration registration) {
    Participant p = registration.participant();
    String body =
        String.join(
            "\n",
            "Dear " + p.firstName() + " " + p.lastName() + ",",
            "",
            "thank you for registering for "
                + app.conferenceName()
                + ". Your registration has been received.",
            "",
            "Registration type: " + registration.type().label(),
            "Selected options:",
            optionLines(registration),
            "",
            "If any of this is wrong, please reply to the organizers.",
            "",
            app.conferenceName() + " organizers",
            "");
    send(List.of(p.email()), "Registration confirmed: " + app.conferenceName(), body, null, null);
  }

  /** Organizer notification with the raw JSON copy attached; throws when it cannot be sent. */
  public void sendOrganizerNotification(Registration registration, byte[] jsonCopy) {
    Participant p = registration.participant();
    StringBuilder fields = new StringBuilder();
    fields.append("First name: ").append(p.firstName()).append('\n');
    fields.append("Last name: ").append(p.lastName()).append('\n');
    fields.append("Email: ").append(p.email());
    if (registration.type() == RegistrationType.EXTERNAL) {
      fields.append("\nOrganization / institution: ").append(p.organization());
    } else {
      fields.append("\nStudy institution: ").append(p.studyInstitution());
      fields.append("\nStudy programme: ").append(p.studyProgramme());
      fields.append("\nStudent ID: ").append(p.studentId());
    }
    StringBuilder consents = new StringBuilder();
    for (GivenConsent consent : registration.consents()) {
      if (!consents.isEmpty()) {
        consents.append("; ");
      }
      consents.append(consent.consentId()).append(" given at ").append(consent.givenAt());
    }
    String body =
        String.join(
            "\n",
            "A new registration was accepted.",
            "",
            "Registration type: " + registration.type().label(),
            "Registered at: " + registration.registeredAt(),
            fields.toString(),
            "Selected options:",
            optionLines(registration),
            "Consents: " + consents,
            "",
            "The raw registration JSON is attached.",
            "");
    send(
        app.organizer().emailList(),
        "New " + registration.type().name() + " registration: " + app.conferenceName(),
        body,
        "registration-" + registration.id() + ".json",
        jsonCopy);
  }

  private static String optionLines(Registration registration) {
    List<SelectedOption> options = registration.options();
    if (options.isEmpty()) {
      return "  (none)";
    }
    StringBuilder lines = new StringBuilder();
    for (Category category : Category.values()) {
      for (SelectedOption option : options) {
        if (option.category() == category) {
          if (!lines.isEmpty()) {
            lines.append('\n');
          }
          lines
              .append("  - ")
              .append(option.displayName())
              .append(" (")
              .append(category.label())
              .append(')');
        }
      }
    }
    return lines.toString();
  }

  private void send(
      List<String> recipients, String subject, String body, String fileName, byte[] attachment) {
    try {
      MimeMessage message = sender.createMimeMessage();
      MimeMessageHelper helper =
          new MimeMessageHelper(message, attachment != null, StandardCharsets.UTF_8.name());
      helper.setFrom(new InternetAddress(app.mail().from(), true));
      InternetAddress[] to = new InternetAddress[recipients.size()];
      for (int i = 0; i < to.length; i++) {
        to[i] = new InternetAddress(recipients.get(i), true);
      }
      helper.setTo(to);
      helper.setSubject(subject);
      helper.setText(body, false);
      if (attachment != null) {
        helper.addAttachment(
            fileName, new ByteArrayResource(attachment), "application/json; charset=UTF-8");
      }
      sender.send(message);
    } catch (MessagingException e) {
      throw new MailPreparationException(e);
    }
  }

  /** A message that could not be built; treated like a send failure (D-13). */
  public static final class MailPreparationException extends MailException {

    private static final long serialVersionUID = 1L;

    MailPreparationException(Throwable cause) {
      super("mail could not be prepared", cause);
    }
  }
}

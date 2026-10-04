package si.konferenca.registration.infrastructure;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import si.konferenca.registration.application.RegistrationNotifier;
import si.konferenca.registration.domain.Participant;
import si.konferenca.registration.domain.Registration;
import si.konferenca.registration.domain.RegistrationType;
import si.konferenca.registration.domain.SelectedOption;

/**
 * Plain-text UTF-8 emails (email-message.schema.json). User input appears only in bodies, never in
 * headers, and bodies are text/plain, so it cannot inject headers or markup (SR-05).
 */
public final class MailRegistrationNotifier implements RegistrationNotifier {

  private static final Logger LOG = LoggerFactory.getLogger(MailRegistrationNotifier.class);

  private final JavaMailSender sender;
  private final String from;
  private final String conferenceName;
  private final List<String> organizers;

  public MailRegistrationNotifier(
      JavaMailSender sender, String from, String conferenceName, List<String> organizers) {
    this.sender = sender;
    this.from = from;
    this.conferenceName = conferenceName;
    this.organizers = List.copyOf(organizers);
  }

  @Override
  public void registrationAccepted(Registration registration, byte[] jsonCopy) {
    send(
        registration,
        "participant confirmation",
        registration.participant().email(),
        "Registration confirmed: " + conferenceName,
        participantText(registration),
        null);
    for (String organizer : organizers) {
      send(
          registration,
          "organizer notification",
          organizer,
          "New registration (" + registration.type().lowerLabel() + "): " + conferenceName,
          organizerText(registration),
          jsonCopy);
    }
  }

  private void send(
      Registration registration,
      String kind,
      String to,
      String subject,
      String text,
      byte[] attachment) {
    try {
      MimeMessage message = sender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(message, attachment != null, "UTF-8");
      helper.setFrom(from);
      helper.setTo(to);
      helper.setSubject(subject);
      helper.setText(text, false);
      if (attachment != null) {
        helper.addAttachment(
            "registration-" + registration.id() + ".json",
            new ByteArrayResource(attachment),
            "application/json");
      }
      sender.send(message);
    } catch (MessagingException | MailException e) {
      LOG.warn(
          "{} for registration {} failed: {}",
          kind,
          registration.id(),
          e.getClass().getSimpleName());
    }
  }

  private String participantText(Registration registration) {
    Participant p = registration.participant();
    StringBuilder text = new StringBuilder(512);
    text.append("Dear ")
        .append(p.firstName())
        .append(' ')
        .append(p.lastName())
        .append(",\n\nthank you for registering for ")
        .append(conferenceName)
        .append(". Your registration was received.\n\n");
    details(text, registration);
    text.append("\nSelected options:\n");
    options(text, registration.options(), false);
    text.append("\nRegistration ID: ").append(registration.id()).append('\n');
    return text.toString();
  }

  private String organizerText(Registration registration) {
    StringBuilder text = new StringBuilder(1024);
    text.append("New registration for ")
        .append(conferenceName)
        .append("\n\nRegistration ID: ")
        .append(registration.id())
        .append("\nAccepted at (UTC): ")
        .append(registration.acceptedAt())
        .append('\n');
    details(text, registration);
    text.append("\nOptions:\n");
    options(text, registration.options(), true);
    text.append("\nConsent: ")
        .append(registration.consentText())
        .append("\nConsent given at (UTC): ")
        .append(registration.consentGivenAt())
        .append("\n\nThe raw registration JSON is attached.\n");
    return text.toString();
  }

  private static void details(StringBuilder text, Registration registration) {
    Participant p = registration.participant();
    line(text, "Registration type", registration.type().label());
    line(text, "First name", p.firstName());
    line(text, "Last name", p.lastName());
    line(text, "Email", p.email());
    if (registration.type() == RegistrationType.EXTERNAL) {
      line(text, "Organization / institution", p.organization());
    } else {
      line(text, "Study institution", p.studyInstitution());
      line(text, "Study programme", p.studyProgramme());
      line(text, "Student ID", p.studentId());
    }
  }

  private static void options(StringBuilder text, List<SelectedOption> options, boolean withIds) {
    if (options.isEmpty()) {
      text.append("- none\n");
    }
    for (SelectedOption option : options) {
      text.append("- ").append(option.optionName());
      if (withIds) {
        text.append(" [").append(option.optionId()).append(']');
      }
      text.append(" (").append(option.category().value()).append(")\n");
    }
  }

  private static void line(StringBuilder text, String label, String value) {
    text.append(label).append(": ").append(value).append('\n');
  }
}

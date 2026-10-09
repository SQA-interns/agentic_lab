package si.konferenca.registration.infrastructure;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import si.konferenca.registration.application.RegistrationNotifier;
import si.konferenca.registration.domain.Category;
import si.konferenca.registration.domain.ParticipantDetails;
import si.konferenca.registration.domain.Registration;
import si.konferenca.registration.domain.SelectedOption;

/**
 * Plain-text UTF-8 emails over SMTP (docs/02_contracts/emails.json; SR-05): user input appears only
 * in bodies, never in headers except the validated participant address.
 */
public class SmtpRegistrationNotifier implements RegistrationNotifier {

  private static final String UTF_8 = "UTF-8";

  private final JavaMailSender mailSender;
  private final String from;
  private final String conferenceName;
  private final List<String> organizerEmails;

  public SmtpRegistrationNotifier(
      JavaMailSender mailSender, String from, String conferenceName, List<String> organizerEmails) {
    this.mailSender = mailSender;
    this.from = from;
    this.conferenceName = conferenceName;
    this.organizerEmails = List.copyOf(organizerEmails);
  }

  @Override
  public void notifyParticipant(Registration registration) {
    ParticipantDetails participant = registration.participant();
    String body =
        "Dear "
            + fullName(participant)
            + ",\n\n"
            + "thank you for registering for "
            + conferenceName
            + ". Your registration has been received.\n\n"
            + fullName(participant)
            + "\n"
            + "Registration type: "
            + registration.type().label()
            + "\n"
            + "Registration ID: "
            + registration.id()
            + "\n\n"
            + "Selected options:\n"
            + options(registration)
            + "\nKind regards,\n"
            + conferenceName
            + "\n";
    send(
        new String[] {participant.email()},
        "Registration confirmation: " + conferenceName,
        body,
        null,
        null);
  }

  @Override
  public void notifyOrganizers(Registration registration, byte[] jsonCopy) {
    ParticipantDetails participant = registration.participant();
    StringBuilder body = new StringBuilder();
    body.append("A new registration was received for ").append(conferenceName).append(".\n\n");
    line(body, "Registration ID", registration.id().toString());
    line(body, "Registered at", registration.registeredAt().toString());
    line(body, "Registration type", registration.type().label());
    line(body, "First name", participant.firstName());
    line(body, "Last name", participant.lastName());
    line(body, "Email", participant.email());
    line(body, "Organization", participant.organization());
    line(body, "Study institution", participant.studyInstitution());
    line(body, "Study programme", participant.studyProgramme());
    line(body, "Student ID", participant.studentId());
    body.append("\nSelected options:\n").append(options(registration));
    body.append("\nConsents:\n");
    registration
        .consents()
        .forEach(
            consent ->
                body.append(consent.consentId())
                    .append(": given at ")
                    .append(consent.givenAt())
                    .append('\n'));
    body.append("\nThe registration as stored is attached as JSON.\n");
    send(
        organizerEmails.toArray(String[]::new),
        "New registration: " + conferenceName,
        body.toString(),
        "registration-" + registration.id() + ".json",
        jsonCopy);
  }

  private void send(
      String[] to, String subject, String text, String attachmentName, byte[] attachment) {
    try {
      MimeMessage message = mailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(message, attachment != null, UTF_8);
      helper.setFrom(from);
      helper.setTo(to);
      helper.setSubject(subject);
      helper.setText(text, false);
      if (attachment != null) {
        helper.addAttachment(attachmentName, new ByteArrayResource(attachment), "application/json");
      }
      mailSender.send(message);
    } catch (MessagingException e) {
      throw new IllegalStateException("Email could not be built", e);
    }
  }

  private static String fullName(ParticipantDetails participant) {
    return participant.firstName() + " " + participant.lastName();
  }

  private static void line(StringBuilder body, String label, String value) {
    if (value != null) {
      body.append(label).append(": ").append(value).append('\n');
    }
  }

  /** Selected option names per category, in category order; or "No options selected". */
  private static String options(Registration registration) {
    if (registration.options().isEmpty()) {
      return "No options selected\n";
    }
    Map<Category, List<String>> byCategory =
        registration.options().stream()
            .collect(
                Collectors.groupingBy(
                    SelectedOption::category,
                    () -> new EnumMap<>(Category.class),
                    Collectors.mapping(SelectedOption::optionName, Collectors.toList())));
    List<String> lines = new ArrayList<>();
    byCategory.forEach(
        (category, names) -> lines.add(category.label() + ": " + String.join("; ", names)));
    return String.join("\n", lines) + "\n";
  }
}

package si.konferenca.registration.integration;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import si.konferenca.registration.domain.OptionCategory;
import si.konferenca.registration.domain.RegistrationSnapshot;
import si.konferenca.registration.domain.RegistrationSnapshot.SnapshotOption;

/**
 * Participant confirmation and organizer notification emails (specification §11). Plain text only;
 * subjects are fixed or contain only the registration id, so participant data cannot reach a
 * header.
 */
public class MailNotifier {

  static final String PARTICIPANT_SUBJECT = "Registration confirmation";
  static final String ORGANIZER_SUBJECT_PREFIX = "New registration ";
  private static final String NO_OPTIONS = "No optional activities selected.";

  private final JavaMailSender sender;
  private final String from;
  private final List<String> organizerEmails;
  private final String conferenceName;

  public MailNotifier(
      JavaMailSender sender, String from, List<String> organizerEmails, String conferenceName) {
    this.sender = sender;
    this.from = from;
    this.organizerEmails = List.copyOf(organizerEmails);
    this.conferenceName = conferenceName;
  }

  public void sendParticipantConfirmation(RegistrationSnapshot registration)
      throws MessagingException {
    MimeMessage message = sender.createMimeMessage();
    MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
    helper.setFrom(from);
    helper.setTo(registration.email());
    helper.setSubject(PARTICIPANT_SUBJECT);
    helper.setText(participantText(registration), false);
    sender.send(message);
  }

  public void sendOrganizerNotification(RegistrationSnapshot registration, byte[] rawJson)
      throws MessagingException {
    MimeMessage message = sender.createMimeMessage();
    MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
    helper.setFrom(from);
    helper.setTo(organizerEmails.toArray(String[]::new));
    helper.setSubject(ORGANIZER_SUBJECT_PREFIX + registration.registrationId());
    helper.setText(organizerText(registration), false);
    helper.addAttachment(
        "registration-" + registration.registrationId() + ".json",
        new ByteArrayResource(rawJson),
        "application/json");
    sender.send(message);
  }

  String participantText(RegistrationSnapshot r) {
    StringBuilder text = new StringBuilder();
    text.append("Dear ").append(r.firstName()).append(' ').append(r.lastName()).append(",\n\n");
    text.append("your registration for ").append(conferenceName).append(" has been received.\n\n");
    text.append("Registration ID: ").append(r.registrationId()).append('\n');
    text.append("Registration type: ").append(r.type().label()).append("\n\n");
    text.append("Selected options:\n");
    appendOptions(text, r.options(), false);
    text.append("\nThank you for registering.\n");
    return text.toString();
  }

  String organizerText(RegistrationSnapshot r) {
    StringBuilder text = new StringBuilder();
    text.append("A new registration was received for ").append(conferenceName).append(".\n\n");
    line(text, "Registration ID", r.registrationId().toString());
    line(text, "Submitted at (UTC)", r.submittedAt().toString());
    line(text, "Registration type", r.type().label());
    line(text, "First name", r.firstName());
    line(text, "Last name", r.lastName());
    line(text, "Email", r.email());
    line(text, "Organization / institution", r.organization());
    line(text, "Study institution", r.studyInstitution());
    line(text, "Study programme", r.studyProgramme());
    line(text, "Student ID", r.studentId());
    line(text, "Personal data consent at (UTC)", r.personalDataConsentAt().toString());
    text.append("\nSelected options:\n");
    appendOptions(text, r.options(), true);
    text.append("\nThe raw registration JSON is attached.\n");
    return text.toString();
  }

  private static void line(StringBuilder text, String label, String value) {
    if (value != null) {
      text.append(label).append(": ").append(value).append('\n');
    }
  }

  private static void appendOptions(
      StringBuilder text, List<SnapshotOption> options, boolean withIds) {
    if (options.isEmpty()) {
      text.append(NO_OPTIONS).append('\n');
      return;
    }
    Map<OptionCategory, List<SnapshotOption>> byCategory =
        options.stream()
            .collect(
                Collectors.groupingBy(
                    SnapshotOption::category,
                    () -> new java.util.EnumMap<>(OptionCategory.class),
                    Collectors.mapping(Function.identity(), Collectors.toList())));
    byCategory.forEach(
        (category, list) -> {
          text.append(category.heading()).append(":\n");
          for (SnapshotOption o : list) {
            text.append("  - ").append(o.name());
            if (withIds) {
              text.append(" [").append(o.id()).append(']');
            }
            text.append('\n');
          }
        });
  }
}

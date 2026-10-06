package si.konferenca.registration.application;

import java.util.List;
import si.konferenca.registration.domain.OptionCategory;
import si.konferenca.registration.domain.Participant;
import si.konferenca.registration.domain.Registration;
import si.konferenca.registration.domain.RegistrationType;

/**
 * Plain-text emails of an accepted registration (docs/02_contracts/emails.json, SR-05, SR-07): the
 * participant confirmation (US-006) and the organizer notification (US-007).
 */
public class RegistrationEmails {

  private final String conferenceName;
  private final List<String> organizerEmails;

  public RegistrationEmails(String conferenceName, List<String> organizerEmails) {
    this.conferenceName = conferenceName;
    this.organizerEmails = List.copyOf(organizerEmails);
  }

  /** A message to send; the attachment is null when there is none. */
  public record EmailMessage(List<String> to, String subject, String text, Attachment attachment) {

    public EmailMessage {
      to = List.copyOf(to);
    }
  }

  /** An attachment with its file name, media type and content. */
  public record Attachment(String filename, String contentType, byte[] content) {

    public Attachment {
      content = content.clone();
    }

    @Override
    public byte[] content() {
      return content.clone();
    }
  }

  public EmailMessage participantConfirmation(Registration r) {
    Participant p = r.participant();
    StringBuilder text = new StringBuilder();
    text.append("Dear ").append(p.fullName()).append(",\n\n");
    text.append("thank you for registering for ").append(conferenceName).append(".\n");
    text.append("Your registration has been received.\n\n");
    text.append("Registration ID: ").append(r.id()).append('\n');
    text.append("Registration type: ").append(r.type().label()).append("\n\n");
    appendOptions(text, r);
    text.append("\nKind regards,\n").append(conferenceName).append('\n');
    return new EmailMessage(
        List.of(p.email()), "Registration confirmed: " + conferenceName, text.toString(), null);
  }

  public EmailMessage organizerNotification(Registration r, byte[] jsonCopy) {
    Participant p = r.participant();
    StringBuilder text = new StringBuilder();
    text.append("A new registration was received.\n\n");
    line(text, "Registration ID", r.id().toString());
    line(text, "Received at (UTC)", r.receivedAt().toString());
    line(text, "Registration type", r.type().label());
    line(text, "First name", p.firstName());
    line(text, "Last name", p.lastName());
    line(text, "Email", p.email());
    if (r.type() == RegistrationType.EXTERNAL) {
      line(text, "Organization / institution", p.organization());
    } else {
      line(text, "Study institution", p.studyInstitution());
      line(text, "Study programme", p.studyProgramme());
      line(text, "Student ID", p.studentId());
    }
    text.append('\n');
    appendOptions(text, r);
    text.append("\nConsents given:\n");
    if (r.consents().isEmpty()) {
      text.append("- none\n");
    }
    for (Registration.GivenConsent c : r.consents()) {
      text.append("- ").append(c.id()).append(" (").append(c.givenAt()).append("): ");
      text.append(c.text()).append('\n');
    }
    text.append("\nThe registration as stored is attached as JSON.\n");
    return new EmailMessage(
        organizerEmails,
        "New registration: " + conferenceName,
        text.toString(),
        new Attachment("registration-" + r.id() + ".json", "application/json", jsonCopy));
  }

  private static void line(StringBuilder text, String label, String value) {
    text.append(label).append(": ").append(value).append('\n');
  }

  private static void appendOptions(StringBuilder text, Registration r) {
    text.append("Selected options:\n");
    if (r.options().isEmpty()) {
      text.append("No options selected\n");
      return;
    }
    for (OptionCategory category : OptionCategory.values()) {
      List<String> names = r.optionNames(category);
      if (!names.isEmpty()) {
        text.append(category.label()).append(": ").append(String.join(", ", names)).append('\n');
      }
    }
  }
}

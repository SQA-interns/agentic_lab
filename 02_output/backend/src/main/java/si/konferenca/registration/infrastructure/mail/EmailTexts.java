package si.konferenca.registration.infrastructure.mail;

import java.util.List;
import java.util.stream.Collectors;
import si.konferenca.registration.domain.ConferenceOption;
import si.konferenca.registration.domain.OptionCategory;
import si.konferenca.registration.domain.Registration;

/** Plain-text email content (emails.schema.json); user input appears only in the body. */
final class EmailTexts {

  private EmailTexts() {}

  static String participantSubject(String conferenceName) {
    return "Registration received: " + conferenceName;
  }

  static String organizerSubject(Registration r, String conferenceName) {
    return "New "
        + r.type().name().toLowerCase(java.util.Locale.ROOT)
        + " registration: "
        + conferenceName;
  }

  static String participantBody(Registration r, String conferenceName) {
    return "Dear "
        + r.firstName()
        + " "
        + r.lastName()
        + ",\n\nthank you for registering for "
        + conferenceName
        + ". Your registration has been received.\n\nRegistration type: "
        + r.type().label()
        + "\nRegistration ID: "
        + r.id()
        + "\n\nSelected options:\n"
        + options(r)
        + "\nKind regards,\nthe organizers\n";
  }

  static String organizerBody(Registration r) {
    StringBuilder b = new StringBuilder();
    line(b, "Registration ID", r.id().toString());
    line(b, "Received at (UTC)", r.receivedAt().toString());
    line(b, "Type", r.type().label());
    line(b, "First name", r.firstName());
    line(b, "Last name", r.lastName());
    line(b, "Email", r.email());
    line(b, "Organization / institution", r.organization());
    line(b, "Study institution", r.studyInstitution());
    line(b, "Study programme", r.studyProgramme());
    line(b, "Student ID", r.studentId());
    b.append("\nSelected options:\n").append(options(r));
    b.append("\nConsent given: ").append(r.consent().text()).append('\n');
    b.append("\nThe registration as stored is attached as JSON.\n");
    return b.toString();
  }

  private static void line(StringBuilder b, String label, String value) {
    if (value != null) {
      b.append(label).append(": ").append(value).append('\n');
    }
  }

  private static String options(Registration r) {
    if (r.options().isEmpty()) {
      return "No options selected.\n";
    }
    StringBuilder b = new StringBuilder();
    for (OptionCategory c : OptionCategory.values()) {
      List<ConferenceOption> selected = r.optionsIn(c);
      if (!selected.isEmpty()) {
        b.append(c.label())
            .append(": ")
            .append(selected.stream().map(ConferenceOption::name).collect(Collectors.joining(", ")))
            .append('\n');
      }
    }
    return b.toString();
  }
}

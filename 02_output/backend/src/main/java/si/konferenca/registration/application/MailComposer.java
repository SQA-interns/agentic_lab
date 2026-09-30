package si.konferenca.registration.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import si.konferenca.registration.domain.Category;
import si.konferenca.registration.domain.Registration;
import si.konferenca.registration.domain.RegistrationOption;
import si.konferenca.registration.domain.RegistrationType;

/**
 * Builds the two plain-text emails (docs/02_contracts/emails.md). User input appears only in
 * bodies; subjects contain configuration and fixed text (SR-05). Only registration data is included
 * (SR-07).
 */
public final class MailComposer {

  static final Map<Category, String> CATEGORY_LABELS =
      Map.of(
          Category.WORKSHOP, "Workshops",
          Category.EVENT, "Events",
          Category.MEAL, "Meals",
          Category.OTHER, "Other activities");

  private MailComposer() {}

  public static OutgoingMail participantMail(Registration r, String conferenceName) {
    String text =
        "Dear "
            + r.firstName()
            + " "
            + r.lastName()
            + ",\n\n"
            + "thank you for registering for "
            + conferenceName
            + ". Your registration has been received.\n\n"
            + "Registration reference: "
            + r.reference()
            + "\n"
            + "Registration type: "
            + typeLabel(r.type())
            + "\n\n"
            + "Selected options:\n"
            + optionLines(r)
            + "\nKind regards,\n"
            + conferenceName
            + " organizers\n";
    return new OutgoingMail(
        List.of(r.email()), "Registration confirmed – " + conferenceName, text, null, null);
  }

  public static OutgoingMail organizerMail(
      Registration r, String conferenceName, List<String> organizers, byte[] jsonCopy) {
    StringBuilder text = new StringBuilder();
    text.append("A new registration was received for ").append(conferenceName).append(".\n\n");
    line(text, "Reference", r.reference().toString());
    line(text, "Submitted at (UTC)", r.submittedAt().toString());
    line(text, "Type", typeLabel(r.type()));
    line(text, "First name", r.firstName());
    line(text, "Last name", r.lastName());
    line(text, "Email", r.email());
    if (r.type() == RegistrationType.EXTERNAL) {
      line(text, "Organization / institution", r.organization());
    } else {
      line(text, "Study institution", r.studyInstitution());
      line(text, "Study programme", r.studyProgramme());
      line(text, "Student ID", r.studentId());
    }
    text.append("\nSelected options:\n").append(optionLines(r)).append("\nConsents given:\n");
    r.consents()
        .forEach(
            c ->
                text.append("- ")
                    .append(c.consentId())
                    .append(" (")
                    .append(c.givenAt())
                    .append("): ")
                    .append(c.consentText())
                    .append('\n'));
    text.append("\nThe registration as submitted is attached as JSON.\n");
    return new OutgoingMail(
        organizers,
        "New registration – " + conferenceName,
        text.toString(),
        "registration-" + r.reference() + ".json",
        jsonCopy);
  }

  static String typeLabel(RegistrationType type) {
    return type == RegistrationType.STUDENT ? "Student" : "External participant";
  }

  private static void line(StringBuilder text, String label, String value) {
    text.append(label).append(": ").append(value).append('\n');
  }

  private static String optionLines(Registration r) {
    if (r.options().isEmpty()) {
      return "- none\n";
    }
    List<String> lines = new ArrayList<>();
    for (Category category : Category.values()) {
      String names =
          r.options().stream()
              .filter(o -> o.category() == category)
              .map(RegistrationOption::optionName)
              .collect(Collectors.joining("; "));
      if (!names.isEmpty()) {
        lines.add("- " + CATEGORY_LABELS.get(category) + ": " + names + "\n");
      }
    }
    return String.join("", lines);
  }
}

package si.konferenca.registration.infrastructure;

import java.util.List;
import si.konferenca.registration.application.RegistrationCopy;

/** Plain-text email bodies (02_contracts/emails.md). User input is never interpreted. */
final class MailTexts {

  private MailTexts() {}

  static String typeLabel(String type) {
    return "STUDENT".equals(type) ? "Student" : "External participant";
  }

  static String categoryLabel(String category) {
    return switch (category) {
      case "workshop" -> "Workshop";
      case "event" -> "Event";
      case "meal" -> "Meal";
      default -> "Other activity";
    };
  }

  static String participantSubject(String conferenceName) {
    return "Registration confirmed: " + conferenceName;
  }

  static String participantBody(RegistrationCopy r, String conferenceName) {
    StringBuilder b = new StringBuilder();
    b.append("Dear ").append(r.firstName()).append(' ').append(r.lastName()).append(",\n\n");
    b.append("your registration for ").append(conferenceName).append(" has been received.\n\n");
    b.append("Registration ID: ").append(r.id()).append('\n');
    b.append("Registration type: ").append(typeLabel(r.type())).append("\n\n");
    appendOptions(b, r.options());
    b.append("\nThis is an automatic message.\n");
    return b.toString();
  }

  static void appendOptions(StringBuilder b, List<RegistrationCopy.Option> options) {
    b.append("Selected options:\n");
    if (options.isEmpty()) {
      b.append("- none\n");
    }
    for (RegistrationCopy.Option o : options) {
      b.append("- ")
          .append(o.name())
          .append(" (")
          .append(categoryLabel(o.category()))
          .append(")\n");
    }
  }
}

package si.konferenca.registration.infrastructure;

import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;
import si.konferenca.registration.domain.RegistrationType;
import si.konferenca.registration.service.RegistrationSnapshot;

/** Plain-text rendering of a registration for emails. */
final class RegistrationText {

  private RegistrationText() {}

  static String describe(RegistrationSnapshot registration) {
    StringBuilder text = new StringBuilder(512);
    line(text, "Registration reference", registration.registrationId().toString());
    line(
        text, "Submitted at (UTC)", DateTimeFormatter.ISO_INSTANT.format(registration.createdAt()));
    line(text, "Registration type", typeLabel(registration.registrationType()));
    line(text, "First name", registration.firstName());
    line(text, "Last name", registration.lastName());
    line(text, "Email", registration.email());
    if (registration.registrationType() == RegistrationType.STUDENT) {
      line(text, "Study institution", registration.studyInstitution());
      line(text, "Study programme", registration.studyProgramme());
      line(text, "Student ID", registration.studentId());
    } else {
      line(text, "Organization / institution", registration.organization());
    }
    line(
        text,
        "Consents",
        registration.consents().entrySet().stream()
            .map(e -> e.getKey() + "=" + (Boolean.TRUE.equals(e.getValue()) ? "yes" : "no"))
            .sorted()
            .collect(Collectors.joining(", ")));
    text.append("Selected options:");
    if (registration.options().isEmpty()) {
      text.append(" none\n");
    } else {
      text.append('\n');
      registration
          .options()
          .forEach(
              o ->
                  text.append("  - ")
                      .append(o.name())
                      .append(" (")
                      .append(o.category())
                      .append(")\n"));
    }
    return text.toString();
  }

  static String typeLabel(RegistrationType type) {
    return type == RegistrationType.STUDENT ? "Student" : "External participant";
  }

  private static void line(StringBuilder text, String label, String value) {
    text.append(label).append(": ").append(value == null ? "" : value).append('\n');
  }
}

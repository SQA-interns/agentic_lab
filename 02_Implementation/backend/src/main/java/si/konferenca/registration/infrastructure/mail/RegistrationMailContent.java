package si.konferenca.registration.infrastructure.mail;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;
import si.konferenca.registration.domain.OptionCategory;
import si.konferenca.registration.domain.Registration;
import si.konferenca.registration.domain.RegistrationType;
import si.konferenca.registration.domain.SelectedOption;

/**
 * Builds plain-text email content. Values are validated and free of control characters, and the
 * messages are sent as text/plain, so no markup is ever interpreted.
 */
final class RegistrationMailContent {

  static final String PARTICIPANT_SUBJECT = "Conference registration confirmation";

  private RegistrationMailContent() {}

  static String organizerSubject(Registration registration) {
    return "New conference registration (" + registration.getType().label() + ")";
  }

  static String participantBody(Registration registration) {
    StringBuilder body = new StringBuilder(512);
    body.append("Dear ")
        .append(registration.getFirstName())
        .append(' ')
        .append(registration.getLastName())
        .append(",\n\n")
        .append("thank you for registering for the conference. ")
        .append("Your registration has been received.\n\n");
    appendDetails(body, registration);
    body.append("\nPlease keep this email as the record of your registration.\n");
    return body.toString();
  }

  static String organizerBody(Registration registration) {
    StringBuilder body = new StringBuilder(512);
    body.append("A new participant has registered for the conference.\n\n");
    appendDetails(body, registration);
    body.append("Consents given: ")
        .append(
            registration.getConsentIds().isEmpty()
                ? "none"
                : String.join(", ", registration.getConsentIds()))
        .append('\n')
        .append("Registered at (UTC): ")
        .append(DateTimeFormatter.ISO_INSTANT.format(registration.getCreatedAt()))
        .append("\n\nThe raw registration data is attached as JSON.\n");
    return body.toString();
  }

  private static void appendDetails(StringBuilder body, Registration registration) {
    line(body, "Registration ID", String.valueOf(registration.getId()));
    line(body, "Registration type", registration.getType().label());
    line(body, "First name", registration.getFirstName());
    line(body, "Last name", registration.getLastName());
    line(body, "Email", registration.getEmail());
    if (registration.getType() == RegistrationType.EXTERNAL) {
      line(body, "Organization / institution", registration.getOrganization());
    } else {
      line(body, "Study institution", registration.getStudyInstitution());
      line(body, "Study programme", registration.getStudyProgramme());
      line(body, "Student ID", registration.getStudentId());
    }
    body.append("\nSelected options:\n");
    if (registration.getSelectedOptions().isEmpty()) {
      body.append("  none\n");
    }
    for (OptionCategory category : OptionCategory.values()) {
      List<SelectedOption> options = registration.getSelectedOptions(category);
      if (!options.isEmpty()) {
        body.append("  ")
            .append(category.label())
            .append(": ")
            .append(
                options.stream()
                    .map(SelectedOption::getOptionName)
                    .collect(Collectors.joining(", ")))
            .append('\n');
      }
    }
  }

  private static void line(StringBuilder body, String label, String value) {
    body.append(label).append(": ").append(value == null ? "" : value).append('\n');
  }
}

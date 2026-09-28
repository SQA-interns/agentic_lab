package org.example.conference.registration.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.example.conference.catalog.Catalog;
import org.example.conference.catalog.CatalogOption;
import org.example.conference.catalog.OptionGroup;
import org.example.conference.notification.EmailKind;
import org.example.conference.notification.EmailMessage;
import org.springframework.stereotype.Component;

/**
 * Composes plain-text emails. Subjects contain no participant data; bodies are plain text (no HTML
 * rendering, so no markup injection). Input was validated to contain no control characters.
 */
@Component
public class RegistrationEmailComposer {

  private static final Map<String, String> FIELD_LABELS =
      Map.of(
          "firstName", "First name",
          "lastName", "Last name",
          "email", "Email",
          "organization", "Organization/institution",
          "studyInstitution", "Study institution",
          "studyProgramme", "Study programme",
          "studentId", "Student ID");

  private final Catalog catalog;

  public RegistrationEmailComposer(Catalog catalog) {
    this.catalog = catalog;
  }

  public List<EmailMessage> compose(
      UUID registrationId,
      ValidatedRegistration v,
      String rawJson,
      List<String> organizerRecipients) {
    List<EmailMessage> messages = new ArrayList<>();
    Map<String, String> fields = v.command().fields();
    String summary = summary(registrationId, v);
    messages.add(
        new EmailMessage(
            registrationId,
            EmailKind.PARTICIPANT_CONFIRMATION,
            fields.get("email"),
            "Registration confirmation - " + catalog.conferenceName(),
            "Dear "
                + fields.get("firstName")
                + " "
                + fields.get("lastName")
                + ",\n\nThank you for registering for "
                + catalog.conferenceName()
                + ". Your registration has been received.\n\n"
                + summary
                + "\nThis is an automated message.\n",
            null,
            null));
    for (String organizer : organizerRecipients) {
      messages.add(
          new EmailMessage(
              registrationId,
              EmailKind.ORGANIZER_NOTIFICATION,
              organizer,
              "New registration - " + catalog.conferenceName(),
              "A new registration was accepted.\n\n"
                  + summary
                  + "\nThe raw JSON record is attached.\n",
              "registration-" + registrationId + ".json",
              rawJson));
    }
    return messages;
  }

  private static String summary(UUID registrationId, ValidatedRegistration v) {
    StringBuilder text = new StringBuilder(512);
    text.append("Registration ID: ")
        .append(registrationId)
        .append("\nParticipant type: ")
        .append(v.command().participantType().name())
        .append('\n');
    v.command()
        .fields()
        .forEach(
            (key, value) ->
                text.append(FIELD_LABELS.getOrDefault(key, key))
                    .append(": ")
                    .append(value)
                    .append('\n'));
    text.append("\nSelected options:\n");
    for (OptionGroup group : OptionGroup.values()) {
      List<CatalogOption> chosen = v.selections().get(group);
      text.append("  ")
          .append(group.key())
          .append(": ")
          .append(
              chosen.isEmpty()
                  ? "-"
                  : chosen.stream().map(CatalogOption::name).collect(Collectors.joining("; ")))
          .append('\n');
    }
    if (!v.grantedConsents().isEmpty()) {
      text.append("\nConsents given: ")
          .append(v.grantedConsents().stream().map(c -> c.id()).collect(Collectors.joining(", ")))
          .append('\n');
    }
    return text.toString();
  }
}

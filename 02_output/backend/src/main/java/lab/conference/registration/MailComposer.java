package lab.conference.registration;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lab.conference.notifications.NotificationKind;
import lab.conference.notifications.NotificationRequest;
import lab.conference.options.CatalogOption;
import lab.conference.options.GroupId;

/**
 * Composes plain-text notifications (BR-07, BR-08, SR-04). User text goes only into bodies, never
 * into subjects or headers; the only user-supplied header value is the validated recipient.
 */
final class MailComposer {

  static final String PARTICIPANT_SUBJECT = "Lab Conference – registration received";

  private MailComposer() {}

  static List<NotificationRequest> compose(
      UUID id, ValidatedRegistration r, String jsonSha256, List<String> organizerEmails) {
    List<NotificationRequest> out = new ArrayList<>();
    out.add(
        new NotificationRequest(
            id,
            NotificationKind.PARTICIPANT,
            r.field("email"),
            PARTICIPANT_SUBJECT,
            participantBody(id, r),
            null,
            null));
    String summary = organizerBody(id, r);
    for (String organizer : organizerEmails) {
      out.add(
          new NotificationRequest(
              id,
              NotificationKind.ORGANIZER,
              organizer,
              "New registration " + id,
              summary,
              "registration-" + id + ".json",
              jsonSha256));
    }
    return out;
  }

  private static String participantBody(UUID id, ValidatedRegistration r) {
    StringBuilder sb = new StringBuilder();
    sb.append("Dear ")
        .append(r.field("firstName"))
        .append(' ')
        .append(r.field("lastName"))
        .append(",\n\n");
    sb.append("your registration for Lab Conference has been received and stored.\n\n");
    sb.append("Registration ID: ").append(id).append('\n');
    sb.append("Form: ").append(r.formType().key()).append("\n\n");
    sb.append("Selected activities:\n");
    appendSelections(sb, r);
    sb.append("\nThis message was generated automatically. Please keep it for your records.\n");
    return sb.toString();
  }

  private static String organizerBody(UUID id, ValidatedRegistration r) {
    StringBuilder sb = new StringBuilder();
    sb.append("A new registration was accepted.\n\n");
    sb.append("Registration ID: ").append(id).append('\n');
    sb.append("Client request ID: ").append(r.clientRequestId()).append('\n');
    sb.append("Form type: ").append(r.formType().key()).append('\n');
    r.fields()
        .forEach(
            (k, v) ->
                sb.append(RegistrationValidator.LABELS.get(k)).append(": ").append(v).append('\n'));
    if (r.consent() != null) {
      sb.append("Consent (")
          .append(r.consent().id())
          .append("): ")
          .append(r.consent().given() ? "yes" : "no")
          .append('\n');
    }
    sb.append("\nSelected activities:\n");
    appendSelections(sb, r);
    sb.append("\nThe raw registration JSON is attached.\n");
    return sb.toString();
  }

  private static void appendSelections(StringBuilder sb, ValidatedRegistration r) {
    boolean any = false;
    for (GroupId g : GroupId.values()) {
      List<CatalogOption> opts = r.selections().getOrDefault(g, List.of());
      if (!opts.isEmpty()) {
        any = true;
        sb.append("- ").append(g.label()).append(": ");
        sb.append(String.join("; ", opts.stream().map(CatalogOption::name).toList())).append('\n');
      }
    }
    if (!any) {
      sb.append("- none\n");
    }
  }
}

package si.konferenca.registration.service;

import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import si.konferenca.registration.config.AppProperties;
import si.konferenca.registration.domain.Category;
import si.konferenca.registration.domain.Participant;
import si.konferenca.registration.domain.Registration;
import si.konferenca.registration.domain.RegistrationType;
import si.konferenca.registration.domain.SelectedOption;
import si.konferenca.registration.infrastructure.JsonCopyStore;
import si.konferenca.registration.infrastructure.MailGateway;

/**
 * Sends the participant confirmation (US-006) and the organizer notification (US-007) after a
 * registration is stored. A failure is logged with the registration id only and changes nothing
 * else (D-13, ES-07).
 */
@Service
public class NotificationService {

  private static final Logger LOG = LoggerFactory.getLogger(NotificationService.class);
  private static final Map<Category, String> HEADINGS =
      Map.of(
          Category.WORKSHOP, "Workshops",
          Category.EVENT, "Events",
          Category.MEAL, "Meals",
          Category.OTHER, "Other activities");

  private final MailGateway mail;
  private final JsonCopyStore copies;
  private final AppProperties properties;

  public NotificationService(MailGateway mail, JsonCopyStore copies, AppProperties properties) {
    this.mail = mail;
    this.copies = copies;
    this.properties = properties;
  }

  @Async
  public void registrationAccepted(Registration registration) {
    send("Participant", registration, () -> participantMail(registration));
    send("Organizer", registration, () -> organizerMail(registration));
  }

  private void send(String kind, Registration registration, MailSupplier supplier) {
    try {
      mail.send(supplier.get());
    } catch (Exception e) {
      LOG.warn(
          "{} email for registration {} failed: {}",
          kind,
          registration.id(),
          e.getClass().getName());
    }
  }

  MailGateway.Mail participantMail(Registration r) {
    String body =
        "Your registration for "
            + properties.conferenceName()
            + " was received.\n\n"
            + participantLines(r)
            + "\n"
            + optionLines(r.options());
    return new MailGateway.Mail(
        List.of(r.participant().email()),
        "Registration confirmed: " + properties.conferenceName(),
        body,
        null);
  }

  MailGateway.Mail organizerMail(Registration r) throws java.io.IOException {
    StringJoiner consents = new StringJoiner(", ");
    r.consents().forEach(c -> consents.add(c.id() + " (" + c.givenAt() + ")"));
    String body =
        "Registration id: "
            + r.id()
            + "\nReceived at: "
            + r.receivedAt()
            + "\n"
            + participantLines(r)
            + "\n"
            + optionLines(r.options())
            + "\nConsents: "
            + consents
            + "\n";
    return new MailGateway.Mail(
        properties.organizer().emailList(),
        "New registration (" + r.type().name() + "): " + properties.conferenceName(),
        body,
        new MailGateway.Attachment("registration-" + r.id() + ".json", copies.read(r.id())));
  }

  private static String participantLines(Registration r) {
    Participant p = r.participant();
    StringBuilder b = new StringBuilder();
    boolean external = r.type() == RegistrationType.EXTERNAL;
    line(b, "Registration type", external ? "External participant" : "Student");
    line(b, "First name", p.firstName());
    line(b, "Last name", p.lastName());
    line(b, "Email", p.email());
    if (external) {
      line(b, "Organization / institution", p.organization());
    } else {
      line(b, "Study institution", p.studyInstitution());
      line(b, "Study programme", p.studyProgramme());
      line(b, "Student ID", p.studentId());
    }
    return b.toString();
  }

  private static String optionLines(List<SelectedOption> options) {
    Map<Category, String> byCategory =
        options.stream()
            .collect(
                Collectors.groupingBy(
                    SelectedOption::category,
                    Collectors.mapping(SelectedOption::name, Collectors.joining("; "))));
    StringBuilder b = new StringBuilder();
    for (Category category : Category.values()) {
      if (byCategory.containsKey(category)) {
        line(b, HEADINGS.get(category), byCategory.get(category));
      }
    }
    return b.toString();
  }

  private static void line(StringBuilder b, String label, String value) {
    b.append(label).append(": ").append(value).append('\n');
  }

  @FunctionalInterface
  private interface MailSupplier {
    MailGateway.Mail get() throws Exception;
  }
}

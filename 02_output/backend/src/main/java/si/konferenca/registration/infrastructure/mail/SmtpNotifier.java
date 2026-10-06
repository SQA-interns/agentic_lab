package si.konferenca.registration.infrastructure.mail;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import si.konferenca.registration.application.Notifier;
import si.konferenca.registration.domain.Registration;

/**
 * Sends the participant confirmation and the organizer notification asynchronously (US-006,
 * US-007). Failures are logged with the registration id only (ES-07, D-14).
 */
public class SmtpNotifier implements Notifier {

  private static final Logger LOG = LoggerFactory.getLogger(SmtpNotifier.class);

  private final JavaMailSender sender;
  private final Executor executor;
  private final String from;
  private final List<String> organizers;
  private final String conferenceName;

  public SmtpNotifier(
      JavaMailSender sender,
      Executor executor,
      String from,
      List<String> organizers,
      String conferenceName) {
    this.sender = sender;
    this.executor = executor;
    this.from = from;
    this.organizers = List.copyOf(organizers);
    this.conferenceName = conferenceName;
  }

  @Override
  public void registrationAccepted(Registration registration, byte[] jsonCopy) {
    submit(registration, "participant confirmation", () -> sendParticipant(registration));
    if (!organizers.isEmpty()) {
      submit(
          registration,
          "organizer notification",
          () -> sendOrganizer(registration, jsonCopy.clone()));
    }
  }

  private void submit(Registration r, String what, MailTask task) {
    try {
      executor.execute(
          () -> {
            try {
              task.run();
            } catch (Exception e) {
              LOG.error(
                  "Sending {} for registration {} failed: {}",
                  what,
                  r.id(),
                  e.getClass().getSimpleName());
            }
          });
    } catch (RejectedExecutionException e) {
      LOG.error("Mail queue full; {} for registration {} not sent", what, r.id());
    }
  }

  private void sendParticipant(Registration r) throws MessagingException {
    MimeMessage message = sender.createMimeMessage();
    MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
    helper.setFrom(from);
    helper.setTo(r.email());
    helper.setSubject(EmailTexts.participantSubject(conferenceName));
    helper.setText(EmailTexts.participantBody(r, conferenceName), false);
    sender.send(message);
  }

  private void sendOrganizer(Registration r, byte[] json) throws MessagingException {
    MimeMessage message = sender.createMimeMessage();
    MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
    helper.setFrom(from);
    helper.setTo(organizers.toArray(String[]::new));
    helper.setSubject(EmailTexts.organizerSubject(r, conferenceName));
    helper.setText(EmailTexts.organizerBody(r), false);
    helper.addAttachment(
        "registration-" + r.id() + ".json", new ByteArrayResource(json), "application/json");
    sender.send(message);
  }

  @FunctionalInterface
  private interface MailTask {
    void run() throws MessagingException;
  }
}

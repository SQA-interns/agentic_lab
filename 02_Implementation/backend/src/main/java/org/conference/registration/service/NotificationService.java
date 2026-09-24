package org.conference.registration.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.util.List;
import java.util.stream.Collectors;
import org.conference.registration.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Participant confirmation (US-006) and organizer notification with JSON attachment (US-007). Runs
 * asynchronously after commit; failures are logged and never affect the stored registration. All
 * mails are plain text, so participant input is never rendered as HTML.
 */
@Service
public class NotificationService {

  private static final Logger LOG = LoggerFactory.getLogger(NotificationService.class);
  private static final String UTF_8 = "UTF-8";

  private final JavaMailSender mailSender;
  private final AppProperties.Mail mail;
  private final String conferenceName;

  public NotificationService(JavaMailSender mailSender, AppProperties properties) {
    this.mailSender = mailSender;
    this.mail = properties.mail();
    this.conferenceName = properties.conferenceName();
  }

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onRegistrationCompleted(RegistrationCompletedEvent event) {
    sendParticipantConfirmation(event.registration());
    sendOrganizerNotification(event.registration(), event.backupJson());
  }

  void sendParticipantConfirmation(BackupDocument registration) {
    try {
      MimeMessage message = mailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(message, false, UTF_8);
      helper.setFrom(mail.from());
      helper.setTo(registration.participant().email());
      helper.setSubject("Registration confirmed – " + conferenceName);
      helper.setText(participantBody(registration), false);
      mailSender.send(message);
      LOG.info("Confirmation email sent for registration {}", registration.id());
    } catch (MailException | MessagingException e) {
      LOG.error("Confirmation email failed for registration {}", registration.id(), e);
    }
  }

  void sendOrganizerNotification(BackupDocument registration, byte[] json) {
    try {
      MimeMessage message = mailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(message, true, UTF_8);
      helper.setFrom(mail.from());
      helper.setTo(mail.organizers().toArray(String[]::new));
      helper.setSubject(
          "New "
              + registration.type().name().toLowerCase(java.util.Locale.ROOT)
              + " registration – "
              + conferenceName);
      helper.setText(organizerBody(registration), false);
      helper.addAttachment(
          "registration-" + registration.id() + ".json",
          new ByteArrayResource(json),
          "application/json");
      mailSender.send(message);
      LOG.info("Organizer notification sent for registration {}", registration.id());
    } catch (MailException | MessagingException e) {
      LOG.error("Organizer notification failed for registration {}", registration.id(), e);
    }
  }

  String participantBody(BackupDocument r) {
    BackupDocument.Participant p = r.participant();
    return "Dear "
        + p.firstName()
        + " "
        + p.lastName()
        + ",\n\n"
        + "your registration for "
        + conferenceName
        + " has been received.\n\n"
        + "Registration ID: "
        + r.id()
        + "\n"
        + "Registration type: "
        + label(r)
        + "\n"
        + "Selected options: "
        + optionList(r.options())
        + "\n\n"
        + "Kind regards,\nThe organizing committee\n";
  }

  String organizerBody(BackupDocument r) {
    BackupDocument.Participant p = r.participant();
    StringBuilder body = new StringBuilder(512);
    body.append("A new registration was received.\n\n");
    line(body, "Registration ID", r.id().toString());
    line(body, "Type", label(r));
    line(body, "Created at (UTC)", r.createdAt().toString());
    line(body, "First name", p.firstName());
    line(body, "Last name", p.lastName());
    line(body, "Email", p.email());
    line(body, "Organization / institution", p.organization());
    line(body, "Study institution", p.studyInstitution());
    line(body, "Study programme", p.studyProgramme());
    line(body, "Student ID", p.studentId());
    line(body, "Privacy consent", r.privacyConsent() ? "yes" : "no");
    line(body, "Selected options", optionList(r.options()));
    body.append("\nThe full registration is attached as JSON.\n");
    return body.toString();
  }

  private static void line(StringBuilder body, String label, String value) {
    if (value != null) {
      body.append(label).append(": ").append(value).append('\n');
    }
  }

  private static String label(BackupDocument r) {
    return switch (r.type()) {
      case EXTERNAL -> "External participant";
      case STUDENT -> "Student";
    };
  }

  private static String optionList(List<BackupDocument.Option> options) {
    return options.isEmpty()
        ? "none"
        : options.stream().map(BackupDocument.Option::name).collect(Collectors.joining(", "));
  }
}

package lab.conference.notifications;

import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lab.conference.platform.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Sends due outbox entries with unbounded, capped exponential backoff (AR-05). Delivery is
 * at-least-once: a crash between SMTP acceptance and the SENT update can repeat a message; the
 * stable Message-ID lets recipients recognise duplicates. SENT means only that the SMTP server
 * accepted the message.
 */
@Component
public final class NotificationDispatcher {

  static final int BATCH = 20;
  static final Duration BASE_DELAY = Duration.ofSeconds(5);
  static final Duration MAX_DELAY = Duration.ofMinutes(15);
  private static final Logger LOG = LoggerFactory.getLogger(NotificationDispatcher.class);
  private final OutboxRepository outbox;
  private final JavaMailSenderImpl mailSender;
  private final AttachmentSource attachments;
  private final TransactionTemplate tx;
  private final Clock clock;
  private final InternetAddress from;

  public NotificationDispatcher(
      OutboxRepository outbox,
      JavaMailSenderImpl mailSender,
      AttachmentSource attachments,
      TransactionTemplate tx,
      Clock clock,
      AppProperties props) {
    this.outbox = outbox;
    this.mailSender = mailSender;
    this.attachments = attachments;
    this.tx = tx;
    this.clock = clock;
    try {
      this.from = new InternetAddress(props.mail().from(), true);
    } catch (MessagingException e) {
      throw new IllegalStateException("MAIL_FROM is not a valid address", e);
    }
  }

  /** Backoff after {@code attempts} failures: min(2^attempts x 5 s, 15 min). */
  static Duration backoff(int attempts) {
    int exponent = Math.min(Math.max(attempts, 1), 20);
    Duration d = BASE_DELAY.multipliedBy(1L << exponent);
    return d.compareTo(MAX_DELAY) > 0 ? MAX_DELAY : d;
  }

  @Scheduled(
      initialDelayString = "${app.notify-poll-interval}",
      fixedDelayString = "${app.notify-poll-interval}")
  public void dispatchDue() {
    try {
      Integer sent = tx.execute(status -> dispatchBatch());
      if (sent != null && sent == BATCH) {
        dispatchDue();
      }
    } catch (RuntimeException e) {
      LOG.warn("Notification dispatch skipped ({})", e.getClass().getSimpleName());
    }
  }

  private int dispatchBatch() {
    Instant now = clock.instant();
    List<OutboxEntry> due = outbox.claimDue(now, BATCH);
    int sent = 0;
    for (OutboxEntry entry : due) {
      try {
        send(entry);
        entry.markSent(clock.instant());
        sent++;
      } catch (MessagingException | MailException | IOException e) {
        int attempt = entry.attempts() + 1;
        entry.markFailed(
            clock.instant(), e.getClass().getSimpleName(), clock.instant().plus(backoff(attempt)));
        LOG.warn(
            "Notification {} for registration {} failed (attempt {}, {})",
            entry.kind(),
            entry.registrationId(),
            attempt,
            e.getClass().getSimpleName());
      }
    }
    return sent;
  }

  void send(OutboxEntry entry) throws MessagingException, IOException {
    String messageId =
        "<"
            + entry.registrationId()
            + "."
            + entry.kind().name().toLowerCase(java.util.Locale.ROOT)
            + "."
            + entry.id()
            + "@lab-conference>";
    MimeMessage message = new StableIdMessage(mailSender.getSession(), messageId);
    boolean attach = entry.attachmentName() != null;
    MimeMessageHelper helper = new MimeMessageHelper(message, attach, "UTF-8");
    helper.setFrom(from);
    helper.setTo(new InternetAddress(entry.recipient(), true));
    helper.setSubject(entry.subject());
    helper.setText(entry.bodyText(), false);
    if (attach) {
      byte[] bytes = attachments.load(entry.registrationId());
      if (!Hashes.sha256(bytes).equals(entry.attachmentSha256())) {
        throw new IOException("attachment does not match the accepted JSON");
      }
      helper.addAttachment(
          entry.attachmentName(), new ByteArrayResource(bytes), "application/json");
    }
    mailSender.send(message);
  }

  /** MimeMessage with a caller-defined, stable Message-ID. */
  private static final class StableIdMessage extends MimeMessage {
    private final String id;

    StableIdMessage(Session session, String id) {
      super(session);
      this.id = id;
    }

    @Override
    protected void updateMessageID() throws MessagingException {
      setHeader("Message-ID", id);
    }
  }
}

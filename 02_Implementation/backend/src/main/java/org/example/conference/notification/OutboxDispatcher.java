package org.example.conference.notification;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Asynchronous at-least-once delivery of outbox records with exponential backoff (AR-05). A crash
 * between SMTP acceptance and the SENT update can cause a duplicate email.
 */
@Component
public class OutboxDispatcher {

  private static final Logger LOG = LoggerFactory.getLogger(OutboxDispatcher.class);
  private static final int MAX_ERROR_LENGTH = 500;

  private final EmailOutboxRepository repository;
  private final JavaMailSender mailSender;
  private final NotificationProperties properties;
  private final TransactionTemplate transactionTemplate;
  private final Clock clock;

  public OutboxDispatcher(
      EmailOutboxRepository repository,
      JavaMailSender mailSender,
      NotificationProperties properties,
      TransactionTemplate transactionTemplate,
      Clock clock) {
    this.repository = repository;
    this.mailSender = mailSender;
    this.properties = properties;
    this.transactionTemplate = transactionTemplate;
    this.clock = clock;
  }

  @Scheduled(
      fixedDelayString = "${app.mail.dispatch-interval:PT5S}",
      initialDelayString = "${app.mail.dispatch-interval:PT5S}")
  public void scheduledDispatch() {
    if (properties.dispatchEnabled()) {
      dispatchDue();
    }
  }

  /** Processes all currently due records; returns the number of processed records. */
  public int dispatchDue() {
    int total = 0;
    int processed;
    do {
      Integer batch = transactionTemplate.execute(status -> dispatchBatch());
      processed = batch == null ? 0 : batch;
      total += processed;
    } while (processed == properties.batchSize());
    return total;
  }

  private int dispatchBatch() {
    List<EmailOutbox> due = repository.lockDue(clock.instant(), properties.batchSize());
    for (EmailOutbox record : due) {
      try {
        mailSender.send(toMime(record));
        record.markSent(clock.instant());
        LOG.info("Email {} ({}) delivered to SMTP", record.getId(), record.getKind());
      } catch (MailException | MessagingException e) {
        Instant next = clock.instant().plus(backoff(record.getAttempts() + 1));
        record.markFailedAttempt(describe(e), next, properties.maxAttempts());
        if (record.getStatus() == EmailStatus.FAILED) {
          LOG.error(
              "Email {} permanently FAILED after {} attempts",
              record.getId(),
              record.getAttempts());
        } else {
          LOG.warn(
              "Email {} attempt {} failed ({}); retry at {}",
              record.getId(),
              record.getAttempts(),
              e.getClass().getSimpleName(),
              next);
        }
      }
    }
    return due.size();
  }

  Duration backoff(int attempt) {
    Duration delay = properties.initialBackoff().multipliedBy(1L << Math.min(attempt - 1, 20));
    return delay.compareTo(properties.maxBackoff()) > 0 ? properties.maxBackoff() : delay;
  }

  private MimeMessage toMime(EmailOutbox record) throws MessagingException {
    MimeMessage mime = mailSender.createMimeMessage();
    boolean multipart = record.getAttachmentName() != null;
    MimeMessageHelper helper =
        new MimeMessageHelper(mime, multipart, StandardCharsets.UTF_8.name());
    helper.setFrom(properties.from());
    helper.setTo(record.getRecipient());
    helper.setSubject(record.getSubject());
    helper.setText(record.getBody(), false);
    if (multipart) {
      helper.addAttachment(
          record.getAttachmentName(),
          new ByteArrayResource(record.getAttachmentContent().getBytes(StandardCharsets.UTF_8)),
          "application/json");
    }
    return mime;
  }

  private static String describe(Exception e) {
    String text = e.getClass().getSimpleName();
    return text.length() > MAX_ERROR_LENGTH ? text.substring(0, MAX_ERROR_LENGTH) : text;
  }
}

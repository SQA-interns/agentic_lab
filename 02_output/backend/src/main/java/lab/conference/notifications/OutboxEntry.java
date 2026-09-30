package lab.conference.notifications;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Durable notification intent (notification_outbox, spec section 5). */
@Entity
@Table(name = "notification_outbox")
public class OutboxEntry {

  /** Delivery state: PENDING until the SMTP server accepted the message. */
  public enum Status {
    PENDING,
    SENT
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "registration_id", nullable = false)
  private UUID registrationId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private NotificationKind kind;

  @Column(nullable = false, length = 254)
  private String recipient;

  @Column(nullable = false, length = 200)
  private String subject;

  @Column(name = "body_text", nullable = false, columnDefinition = "text")
  private String bodyText;

  @Column(name = "attachment_name", length = 100)
  private String attachmentName;

  @Column(name = "attachment_sha256", length = 64)
  private String attachmentSha256;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private Status status;

  @Column(nullable = false)
  private int attempts;

  @Column(name = "next_attempt_at", nullable = false)
  private Instant nextAttemptAt;

  @Column(name = "last_attempt_at")
  private Instant lastAttemptAt;

  @Column(name = "sent_at")
  private Instant sentAt;

  @Column(name = "last_error", length = 200)
  private String lastError;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected OutboxEntry() {}

  OutboxEntry(NotificationRequest r, Instant now) {
    this.registrationId = r.registrationId();
    this.kind = r.kind();
    this.recipient = r.recipient();
    this.subject = r.subject();
    this.bodyText = r.body();
    this.attachmentName = r.attachmentName();
    this.attachmentSha256 = r.attachmentSha256();
    this.status = Status.PENDING;
    this.attempts = 0;
    this.nextAttemptAt = now;
    this.createdAt = now;
  }

  void markSent(Instant now) {
    status = Status.SENT;
    attempts++;
    lastAttemptAt = now;
    sentAt = now;
  }

  void markFailed(Instant now, String error, Instant nextAttempt) {
    attempts++;
    lastAttemptAt = now;
    lastError = error;
    nextAttemptAt = nextAttempt;
  }

  public Long id() {
    return id;
  }

  public UUID registrationId() {
    return registrationId;
  }

  public NotificationKind kind() {
    return kind;
  }

  public String recipient() {
    return recipient;
  }

  public String subject() {
    return subject;
  }

  public String bodyText() {
    return bodyText;
  }

  public String attachmentName() {
    return attachmentName;
  }

  public String attachmentSha256() {
    return attachmentSha256;
  }

  public Status status() {
    return status;
  }

  public int attempts() {
    return attempts;
  }

  public Instant nextAttemptAt() {
    return nextAttemptAt;
  }
}

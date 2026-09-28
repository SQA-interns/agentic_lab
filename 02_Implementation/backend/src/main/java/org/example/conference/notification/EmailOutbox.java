package org.example.conference.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Durable email delivery intent (AR-05). */
@Entity
@Table(name = "email_outbox")
public class EmailOutbox {

  @Id private UUID id;

  @Column(name = "registration_id", nullable = false)
  private UUID registrationId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private EmailKind kind;

  @Column(nullable = false)
  private String recipient;

  @Column(nullable = false)
  private String subject;

  @Column(nullable = false)
  private String body;

  @Column(name = "attachment_name")
  private String attachmentName;

  @Column(name = "attachment_content")
  private String attachmentContent;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private EmailStatus status;

  @Column(nullable = false)
  private int attempts;

  @Column(name = "next_attempt_at", nullable = false)
  private Instant nextAttemptAt;

  @Column(name = "last_error")
  private String lastError;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "sent_at")
  private Instant sentAt;

  protected EmailOutbox() {}

  EmailOutbox(EmailMessage message, Instant now) {
    this.id = UUID.randomUUID();
    this.registrationId = message.registrationId();
    this.kind = message.kind();
    this.recipient = message.recipient();
    this.subject = message.subject();
    this.body = message.body();
    this.attachmentName = message.attachmentName();
    this.attachmentContent = message.attachmentContent();
    this.status = EmailStatus.PENDING;
    this.attempts = 0;
    this.nextAttemptAt = now;
    this.createdAt = now;
  }

  void markSent(Instant now) {
    this.status = EmailStatus.SENT;
    this.attempts++;
    this.sentAt = now;
    this.lastError = null;
  }

  void markFailedAttempt(String error, Instant nextAttempt, int maxAttempts) {
    this.attempts++;
    this.lastError = error;
    if (attempts >= maxAttempts) {
      this.status = EmailStatus.FAILED;
    } else {
      this.nextAttemptAt = nextAttempt;
    }
  }

  public UUID getId() {
    return id;
  }

  public UUID getRegistrationId() {
    return registrationId;
  }

  public EmailKind getKind() {
    return kind;
  }

  public String getRecipient() {
    return recipient;
  }

  public String getSubject() {
    return subject;
  }

  public String getBody() {
    return body;
  }

  public String getAttachmentName() {
    return attachmentName;
  }

  public String getAttachmentContent() {
    return attachmentContent;
  }

  public EmailStatus getStatus() {
    return status;
  }

  public int getAttempts() {
    return attempts;
  }

  public Instant getNextAttemptAt() {
    return nextAttemptAt;
  }

  public String getLastError() {
    return lastError;
  }

  public Instant getSentAt() {
    return sentAt;
  }
}

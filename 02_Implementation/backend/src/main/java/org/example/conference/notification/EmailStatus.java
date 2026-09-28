package org.example.conference.notification;

/** Delivery state of an outbox record; SENT means accepted by the SMTP server. */
public enum EmailStatus {
  PENDING,
  SENT,
  FAILED
}

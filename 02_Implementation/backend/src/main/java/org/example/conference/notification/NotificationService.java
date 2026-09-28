package org.example.conference.notification;

import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Public API of the notification module: enqueue emails inside the caller's transaction. */
@Service
public class NotificationService {

  private final EmailOutboxRepository repository;
  private final NotificationProperties properties;
  private final Clock clock;

  public NotificationService(
      EmailOutboxRepository repository, NotificationProperties properties, Clock clock) {
    this.repository = repository;
    this.properties = properties;
    this.clock = clock;
  }

  /** Must run inside the registration transaction so intent and registration commit together. */
  @Transactional(propagation = Propagation.MANDATORY)
  public void enqueue(List<EmailMessage> messages) {
    repository.saveAll(messages.stream().map(m -> new EmailOutbox(m, clock.instant())).toList());
  }

  public List<String> organizerRecipients() {
    return properties.organizerRecipients();
  }
}

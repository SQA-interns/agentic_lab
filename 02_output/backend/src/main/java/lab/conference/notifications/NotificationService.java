package lab.conference.notifications;

import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Records notification intents inside the caller's acceptance transaction (AR-05, BR-07). */
@Service
public class NotificationService {

  private final OutboxRepository outbox;
  private final Clock clock;

  public NotificationService(OutboxRepository outbox, Clock clock) {
    this.outbox = outbox;
    this.clock = clock;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void enqueue(List<NotificationRequest> requests) {
    var now = clock.instant();
    outbox.saveAll(requests.stream().map(r -> new OutboxEntry(r, now)).toList());
  }
}

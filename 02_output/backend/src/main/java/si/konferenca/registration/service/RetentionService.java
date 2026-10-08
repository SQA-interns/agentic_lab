package si.konferenca.registration.service;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import si.konferenca.registration.config.AppProperties;
import si.konferenca.registration.infrastructure.JsonCopyStore;
import si.konferenca.registration.persistence.RegistrationStore;

/**
 * Deletes registrations and their JSON copies once they are older than the retention period (D-16,
 * SB-13). Runs at startup and then on {@code RETENTION_CRON}.
 */
@Service
public class RetentionService {

  private static final Logger LOG = LoggerFactory.getLogger(RetentionService.class);

  private final RegistrationStore store;
  private final JsonCopyStore copies;
  private final TransactionTemplate transaction;
  private final Duration retention;
  private final Clock clock;

  @Autowired
  public RetentionService(
      RegistrationStore store,
      JsonCopyStore copies,
      TransactionTemplate transaction,
      AppProperties properties) {
    this(store, copies, transaction, properties, Clock.systemUTC());
  }

  RetentionService(
      RegistrationStore store,
      JsonCopyStore copies,
      TransactionTemplate transaction,
      AppProperties properties,
      Clock clock) {
    this.store = store;
    this.copies = copies;
    this.transaction = transaction;
    this.retention = Duration.ofDays(properties.retention().days());
    this.clock = clock;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void atStartup() {
    purge();
  }

  @Scheduled(cron = "${app.retention.cron}")
  public void scheduled() {
    purge();
  }

  /** Deletes every expired registration; returns how many were deleted. */
  public int purge() {
    Instant cutoff = clock.instant().minus(retention);
    List<UUID> expired = store.idsReceivedBefore(cutoff);
    int deleted = 0;
    for (UUID id : expired) {
      try {
        transaction.executeWithoutResult(status -> store.delete(id));
        copies.delete(id);
        deleted++;
      } catch (IOException | RuntimeException e) {
        LOG.warn("Retention of registration {} failed: {}", id, e.getClass().getName());
      }
    }
    if (deleted > 0) {
      LOG.info("Retention deleted {} registrations older than {}", deleted, cutoff);
    }
    return deleted;
  }
}

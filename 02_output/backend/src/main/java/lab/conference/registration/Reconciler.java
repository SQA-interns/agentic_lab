package lab.conference.registration;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lab.conference.platform.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Crash-leftover reconciliation (AR-04, AC-005-04): JSON files without a DB row move to orphaned/,
 * abandoned staging files are deleted, and DB rows whose JSON is missing or altered are reported by
 * registration ID only.
 */
@Component
public class Reconciler {

  private static final Logger LOG = LoggerFactory.getLogger(Reconciler.class);
  private final JsonStore store;
  private final RegistrationRepository repository;
  private final Clock clock;
  private final AppProperties props;

  public Reconciler(
      JsonStore store, RegistrationRepository repository, Clock clock, AppProperties props) {
    this.store = store;
    this.repository = repository;
    this.clock = clock;
    this.props = props;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void atStartup() {
    run();
    verifyAcceptedFiles();
  }

  @Scheduled(
      initialDelayString = "${app.reconcile-interval}",
      fixedDelayString = "${app.reconcile-interval}")
  public void run() {
    Instant cutoff = clock.instant().minus(props.reconcileGrace());
    try {
      int quarantined = 0;
      for (UUID id : store.settledFilesOlderThan(cutoff)) {
        if (!repository.existsById(id)) {
          store.quarantine(id);
          quarantined++;
          LOG.warn("Moved JSON without database registration to orphaned/: {}", id);
        }
      }
      int removed = store.cleanStaging(cutoff);
      if (quarantined + removed > 0) {
        LOG.info("Reconciliation: {} orphaned, {} staging files removed", quarantined, removed);
      }
    } catch (IOException | DataAccessException e) {
      LOG.warn("Reconciliation skipped ({})", e.getClass().getSimpleName());
    }
  }

  /** Reports accepted rows whose JSON file is missing or does not match the stored hash. */
  void verifyAcceptedFiles() {
    try {
      int problems = 0;
      for (RegistrationEntity r : repository.findAllInAcceptanceOrder()) {
        try {
          if (!lab.conference.notifications.Hashes.sha256(store.read(r.id()))
              .equals(r.jsonSha256())) {
            problems++;
            LOG.error("JSON for registration {} does not match its database hash", r.id());
          }
        } catch (IOException e) {
          problems++;
          LOG.error("JSON for registration {} is missing", r.id());
        }
      }
      if (problems > 0) {
        LOG.error("{} accepted registration(s) need recovery from backup", problems);
      }
    } catch (DataAccessException e) {
      LOG.warn("JSON verification skipped ({})", e.getClass().getSimpleName());
    }
  }
}

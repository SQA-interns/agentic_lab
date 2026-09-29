package si.konferenca.registration.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionTemplate;
import si.konferenca.registration.domain.ConferenceOption;
import si.konferenca.registration.domain.Registration;
import si.konferenca.registration.domain.RegistrationSnapshot;
import si.konferenca.registration.domain.RegistrationSnapshot.SnapshotOption;
import si.konferenca.registration.integration.ExcelExportWriter;
import si.konferenca.registration.integration.JsonBackupStore;
import si.konferenca.registration.persistence.ConferenceOptionRepository;
import si.konferenca.registration.persistence.RegistrationRepository;

/** Organizer-only operations: Excel export (US-008) and restore from JSON backups (US-005). */
public class OrganizerService {

  private static final Logger LOG = LoggerFactory.getLogger(OrganizerService.class);

  /** Sort position for options recreated from a backup; they sort after configured options. */
  private static final int RESTORED_OPTION_SORT_ORDER = 1_000_000;

  private final RegistrationRepository registrations;
  private final ConferenceOptionRepository options;
  private final ExcelExportWriter excel;
  private final JsonBackupStore backups;
  private final TransactionTemplate transactions;

  public OrganizerService(
      RegistrationRepository registrations,
      ConferenceOptionRepository options,
      ExcelExportWriter excel,
      JsonBackupStore backups,
      TransactionTemplate transactions) {
    this.registrations = registrations;
    this.options = options;
    this.excel = excel;
    this.backups = backups;
    this.transactions = transactions;
  }

  /** The current registrations as an .xlsx workbook. */
  public byte[] exportWorkbook() throws IOException {
    List<Registration> all =
        transactions.execute(status -> registrations.findAllByOrderBySubmittedAtAscIdAsc());
    return excel.write(all == null ? List.of() : all);
  }

  /** Outcome of a restore run. */
  public record RestoreResult(int restored, int alreadyPresent, int failed) {}

  /**
   * Inserts every backed-up registration that is missing from the database, preserving its id,
   * timestamps, fields and option links. Idempotent; sends no email.
   */
  public RestoreResult restoreFromBackups() throws IOException {
    int restored = 0;
    int alreadyPresent = 0;
    int failed = 0;
    for (JsonBackupStore.Entry entry : backups.readAll()) {
      if (!entry.readable()) {
        failed++;
        LOG.warn("Backup file {} is not a valid registration backup", entry.file().getFileName());
        continue;
      }
      RegistrationSnapshot snapshot = entry.snapshot();
      if (registrations.existsById(snapshot.registrationId())) {
        alreadyPresent++;
        continue;
      }
      try {
        transactions.executeWithoutResult(status -> insert(snapshot));
        restored++;
      } catch (RuntimeException e) {
        failed++;
        LOG.warn("Registration {} could not be restored from backup", snapshot.registrationId());
      }
    }
    LOG.info(
        "Restore finished: {} restored, {} already present, {} failed",
        restored,
        alreadyPresent,
        failed);
    return new RestoreResult(restored, alreadyPresent, failed);
  }

  private void insert(RegistrationSnapshot snapshot) {
    List<ConferenceOption> linked = new ArrayList<>();
    for (SnapshotOption o : snapshot.options()) {
      ConferenceOption option =
          options
              .findById(o.id())
              .orElseGet(
                  () ->
                      options.save(
                          new ConferenceOption(
                              o.id(), o.category(), o.name(), false, RESTORED_OPTION_SORT_ORDER)));
      linked.add(option);
    }
    registrations.saveAndFlush(
        new Registration(
            snapshot.registrationId(),
            snapshot.type(),
            snapshot.participant(),
            snapshot.personalDataConsentAt(),
            snapshot.submittedAt(),
            linked));
  }
}

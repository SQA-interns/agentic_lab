package org.example.conference.registration.backup;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.example.conference.registration.domain.Registration;
import org.example.conference.registration.domain.RegistrationRepository;
import org.example.conference.registration.domain.RegistrationRepository.RegistrationDigest;
import org.example.conference.registration.service.Hashing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Crash-leftover reconciliation between PostgreSQL and the backup volume (AC-005-05). Runs at
 * startup (no grace: no requests in flight yet) and periodically with a grace period.
 */
@Component
public class BackupReconciler {

  private static final Logger LOG = LoggerFactory.getLogger(BackupReconciler.class);

  private final BackupStore store;
  private final RegistrationRepository repository;
  private final BackupProperties properties;
  private final Clock clock;

  public BackupReconciler(
      BackupStore store,
      RegistrationRepository repository,
      BackupProperties properties,
      Clock clock) {
    this.store = store;
    this.repository = repository;
    this.properties = properties;
    this.clock = clock;
  }

  /** Result counters of one reconciliation run. */
  public record Report(
      int tempFilesDeleted, int orphansQuarantined, int filesRestored, int mismatchesRepaired) {}

  @EventListener(ApplicationReadyEvent.class)
  public void onStartup() {
    reconcile(Duration.ZERO);
  }

  @Scheduled(
      fixedDelayString = "${app.backup.reconcile-interval:PT10M}",
      initialDelayString = "${app.backup.reconcile-interval:PT10M}")
  public void periodic() {
    reconcile(properties.reconcileGrace());
  }

  public synchronized Report reconcile(Duration grace) {
    Instant cutoff = clock.instant().minus(grace);
    try {
      int temps = cleanStaging(cutoff);
      Map<UUID, String> digests =
          repository.findAllDigests().stream()
              .collect(
                  Collectors.toMap(
                      RegistrationDigest::getId,
                      RegistrationDigest::getRawJsonSha256,
                      (a, b) -> a));
      int orphans = quarantineOrphans(digests.keySet(), cutoff);
      int[] repaired = repairMissingOrMismatched(digests);
      Report report = new Report(temps, orphans, repaired[0], repaired[1]);
      if (temps + orphans + repaired[0] + repaired[1] > 0) {
        LOG.warn("Backup reconciliation performed changes: {}", report);
      } else {
        LOG.info("Backup reconciliation: consistent ({} registrations)", digests.size());
      }
      return report;
    } catch (IOException e) {
      LOG.error("Backup reconciliation failed: {}", e.getClass().getName());
      return new Report(0, 0, 0, 0);
    }
  }

  private int cleanStaging(Instant cutoff) throws IOException {
    int count = 0;
    try (DirectoryStream<Path> files = Files.newDirectoryStream(store.stagingDirectory())) {
      for (Path file : files) {
        if (isOlderThan(file, cutoff)) {
          Files.deleteIfExists(file);
          count++;
        }
      }
    }
    return count;
  }

  private int quarantineOrphans(Set<UUID> known, Instant cutoff) throws IOException {
    Set<Path> candidates = new HashSet<>();
    try (DirectoryStream<Path> files = Files.newDirectoryStream(store.registrationsDirectory())) {
      for (Path file : files) {
        Optional<UUID> id = idOf(file);
        if ((id.isEmpty() || !known.contains(id.get())) && isOlderThan(file, cutoff)) {
          candidates.add(file);
        }
      }
    }
    if (candidates.isEmpty()) {
      return 0;
    }
    if (known.isEmpty()) {
      LOG.error(
          "Database holds no registrations but {} backup files exist; not quarantining"
              + " (possible database restore scenario, operator action required)",
          candidates.size());
      return 0;
    }
    for (Path file : candidates) {
      String name = fileName(file);
      LOG.warn("Quarantining unaccepted backup file {}", name);
      store.quarantine(file, name);
    }
    return candidates.size();
  }

  private int[] repairMissingOrMismatched(Map<UUID, String> digests) throws IOException {
    int restored = 0;
    int mismatched = 0;
    for (Map.Entry<UUID, String> entry : digests.entrySet()) {
      UUID id = entry.getKey();
      Optional<String> content = store.read(id);
      if (content.isPresent() && Hashing.sha256(content.get()).equals(entry.getValue())) {
        continue;
      }
      Registration registration = repository.findById(id).orElseThrow();
      if (content.isPresent()) {
        store.quarantine(store.file(id), id + ".mismatch-" + clock.millis() + ".json");
        mismatched++;
      } else {
        restored++;
      }
      store.publish(id, registration.getRawJson());
      LOG.warn("Re-published backup file for registration {} from database", id);
    }
    return new int[] {restored, mismatched};
  }

  private static Optional<UUID> idOf(Path file) {
    String name = fileName(file);
    if (!name.endsWith(BackupStore.JSON_SUFFIX)) {
      return Optional.empty();
    }
    try {
      return Optional.of(
          UUID.fromString(name.substring(0, name.length() - BackupStore.JSON_SUFFIX.length())));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  private static String fileName(Path file) {
    Path name = file.getFileName();
    return name == null ? "" : name.toString();
  }

  private static boolean isOlderThan(Path file, Instant cutoff) throws IOException {
    return !Files.getLastModifiedTime(file).toInstant().isAfter(cutoff);
  }
}

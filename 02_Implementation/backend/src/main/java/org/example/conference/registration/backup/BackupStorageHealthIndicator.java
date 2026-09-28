package org.example.conference.registration.backup;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/** Readiness contributor: the backup volume must be writable (AC-X-05). */
@Component("backupStorage")
public class BackupStorageHealthIndicator implements HealthIndicator {

  private final BackupStore store;

  public BackupStorageHealthIndicator(BackupStore store) {
    this.store = store;
  }

  @Override
  public Health health() {
    return store.isWritable() ? Health.up().build() : Health.down().build();
  }
}

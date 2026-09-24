package org.conference.registration.service;

/**
 * Published inside the registration transaction; listeners run only after commit (US-006, US-007).
 */
public record RegistrationCompletedEvent(BackupDocument registration, byte[] backupJson) {

  public RegistrationCompletedEvent {
    backupJson = backupJson.clone();
  }

  @Override
  public byte[] backupJson() {
    return backupJson.clone();
  }
}

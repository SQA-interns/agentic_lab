package si.konferenca.registration.application;

import java.util.UUID;

/** The registration could not be stored; nothing of it was kept (AC-004-03, AC-005-03/04). */
public class StorageFailedException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final UUID registrationId;

  public StorageFailedException(UUID registrationId, Throwable cause) {
    super("registration " + registrationId + " could not be stored", cause);
    this.registrationId = registrationId;
  }

  public UUID registrationId() {
    return registrationId;
  }
}

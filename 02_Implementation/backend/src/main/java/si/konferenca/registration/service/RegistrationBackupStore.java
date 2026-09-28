package si.konferenca.registration.service;

import java.time.Instant;
import java.util.UUID;

/** Port: raw JSON backup of accepted registrations on persistent storage. */
public interface RegistrationBackupStore {

  /** Stores the JSON backup; throws if the backup cannot be written. */
  void store(UUID registrationId, Instant createdAt, byte[] json);

  /** Removes a previously stored backup (best effort). */
  void delete(UUID registrationId, Instant createdAt);
}

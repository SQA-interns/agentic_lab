package si.konferenca.registration.application;

import java.util.UUID;
import si.konferenca.registration.domain.Registration;

/** Port: the raw JSON copy on persistent storage (BR-07, registration-copy.schema.json). */
public interface JsonCopyStore {

  /**
   * Writes the copy atomically.
   *
   * @throws StorageException when the copy cannot be written
   */
  void write(Registration registration);

  /** The stored copy's bytes, exactly as written. */
  byte[] read(UUID reference);

  /** Removes a copy whose registration was not committed. */
  void delete(UUID reference);

  /** True when copies can currently be written (readiness). */
  boolean writable();
}

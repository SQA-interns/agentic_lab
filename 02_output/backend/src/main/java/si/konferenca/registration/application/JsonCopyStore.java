package si.konferenca.registration.application;

import java.util.UUID;

/** Port: the raw JSON copy of each accepted registration on persistent storage (BR-07). */
public interface JsonCopyStore {

  /**
   * Writes the copy as {@code <id>.json} and returns the exact bytes written.
   *
   * @throws StorageException if the file cannot be written
   */
  byte[] write(RegistrationCopy copy);

  /** Removes the copy again, for example when the database transaction fails afterwards. */
  void delete(UUID id);
}

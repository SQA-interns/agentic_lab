package si.konferenca.registration.application;

import si.konferenca.registration.domain.Registration;

/** Port: the raw JSON copy on persistent storage (BR-07, AR-05). */
public interface JsonCopyStore {

  /**
   * Writes the copy durably inside the caller's transaction and returns the bytes written. The copy
   * is removed again if the transaction rolls back.
   *
   * @throws StorageException if the copy cannot be written
   */
  byte[] write(Registration registration);
}

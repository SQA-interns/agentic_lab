package si.konferenca.registration.domain;

import java.util.UUID;

/** Port: the raw JSON copies of accepted registrations on persistent storage (BR-07). */
public interface JsonCopyStore {

  /**
   * Writes the copy of the registration durably.
   *
   * @throws RuntimeException when the copy could not be written; no partial copy remains
   */
  void write(Registration registration);

  /** The bytes of the stored copy. */
  byte[] read(UUID id);

  /** Removes the copy if there is one; never fails. */
  void delete(UUID id);
}

package si.konferenca.registration.application;

import java.util.UUID;
import si.konferenca.registration.domain.Registration;

/** The raw JSON copy on persistent storage (port; BR-07, registration-copy.schema.json). */
public interface RegistrationCopyStore {

  /**
   * Writes the copy atomically and returns its exact bytes.
   *
   * @throws CopyStoreException when the copy cannot be written
   */
  byte[] write(Registration registration);

  /** Removes a copy whose registration could not be committed; missing copies are ignored. */
  void delete(UUID registrationId);

  /** Thrown when a copy cannot be written. */
  final class CopyStoreException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public CopyStoreException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}

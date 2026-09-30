package si.konferenca.registration.application;

import java.io.Serial;

/** The registration could not be stored completely (AR-05); the transaction is rolled back. */
public class StorageException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  public StorageException(String message, Throwable cause) {
    super(message, cause);
  }
}

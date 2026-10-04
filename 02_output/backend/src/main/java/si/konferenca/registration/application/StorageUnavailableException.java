package si.konferenca.registration.application;

import java.io.Serial;

/** The database row or the JSON copy could not be written; nothing was kept (AR-05). */
public class StorageUnavailableException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  public StorageUnavailableException(Throwable cause) {
    super("registration storage unavailable", cause);
  }
}

package si.konferenca.registration.domain;

/** The registration could not be stored; it is not accepted (AR-05). */
public class StorageFailedException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public StorageFailedException(String message, Throwable cause) {
    super(message, cause);
  }
}

package si.konferenca.registration.application;

/** The registration could not be stored; nothing was kept. */
public class StorageException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public StorageException(String message, Throwable cause) {
    super(message, cause);
  }
}

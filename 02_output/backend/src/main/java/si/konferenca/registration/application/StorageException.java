package si.konferenca.registration.application;

/** A registration could not be stored completely; nothing was accepted (AR-05). */
public class StorageException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public StorageException(String message, Throwable cause) {
    super(message, cause);
  }
}

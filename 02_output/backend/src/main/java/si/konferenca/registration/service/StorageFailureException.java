package si.konferenca.registration.service;

/** The registration could not be stored; nothing was kept (AC-005-03, 500). */
public class StorageFailureException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public StorageFailureException(Throwable cause) {
    super("registration could not be stored", cause);
  }
}

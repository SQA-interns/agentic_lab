package si.konferenca.registration.application;

/** Thrown when an accepted registration could not be stored in the database or backup. */
public class RegistrationStorageException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public RegistrationStorageException(String message, Throwable cause) {
    super(message, cause);
  }
}

package si.konferenca.registration.application;

/** Another registration with the same normalised email exists (D-18). */
public class DuplicateEmailException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public DuplicateEmailException(Throwable cause) {
    super("email already registered", cause);
  }
}

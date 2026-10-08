package si.konferenca.registration.service;

/** The email address is already registered (D-15, 409). */
public class DuplicateEmailException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public DuplicateEmailException() {
    super("email already registered", null, false, false);
  }
}

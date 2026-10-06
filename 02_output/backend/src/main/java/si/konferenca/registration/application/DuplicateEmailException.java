package si.konferenca.registration.application;

/** A registration with the same email address is already stored (D-16). */
public class DuplicateEmailException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public DuplicateEmailException() {
    super("Email already registered");
  }
}

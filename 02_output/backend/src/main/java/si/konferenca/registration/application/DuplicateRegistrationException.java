package si.konferenca.registration.application;

/** A registration with the same email already exists (D-10, AC-001-09). */
public class DuplicateRegistrationException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public DuplicateRegistrationException() {
    super("Duplicate registration");
  }
}

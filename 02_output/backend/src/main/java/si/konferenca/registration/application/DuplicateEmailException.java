package si.konferenca.registration.application;

/** An accepted registration already uses the email address (D-13). */
public final class DuplicateEmailException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  public DuplicateEmailException() {
    super("Email address already registered");
  }
}

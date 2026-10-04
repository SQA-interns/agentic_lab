package si.konferenca.registration.application;

import java.io.Serial;

/** A registration already exists for the email address (D-10); nothing stored. */
public class EmailAlreadyRegisteredException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  public EmailAlreadyRegisteredException() {
    super("email already registered");
  }
}

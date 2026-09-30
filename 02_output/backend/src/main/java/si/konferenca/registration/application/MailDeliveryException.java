package si.konferenca.registration.application;

import java.io.Serial;

/** The mail server did not accept a message. */
public class MailDeliveryException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  public MailDeliveryException(String message, Throwable cause) {
    super(message, cause);
  }
}

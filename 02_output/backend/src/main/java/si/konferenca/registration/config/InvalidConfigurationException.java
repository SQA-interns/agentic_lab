package si.konferenca.registration.config;

/** A setting that stops the application from starting; the message never contains a value. */
public class InvalidConfigurationException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public InvalidConfigurationException(String message) {
    super(message);
  }
}

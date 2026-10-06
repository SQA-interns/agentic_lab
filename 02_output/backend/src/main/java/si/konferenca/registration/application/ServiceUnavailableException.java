package si.konferenca.registration.application;

/** Storage or anti-automation verification is unavailable; nothing was accepted. */
public class ServiceUnavailableException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public ServiceUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}

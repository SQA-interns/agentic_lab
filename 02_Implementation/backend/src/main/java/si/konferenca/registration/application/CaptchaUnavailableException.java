package si.konferenca.registration.application;

/** Thrown when the anti-automation service cannot be reached. */
public class CaptchaUnavailableException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public CaptchaUnavailableException(Throwable cause) {
    super("reCAPTCHA verification service unavailable", cause);
  }
}

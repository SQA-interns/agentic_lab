package si.konferenca.registration.application;

/** Thrown when the anti-automation check rejects the submission. */
public class CaptchaFailedException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public CaptchaFailedException() {
    super("reCAPTCHA verification failed");
  }
}

package si.konferenca.registration.service;

/** The anti-automation token was missing or rejected. */
public class CaptchaVerificationException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public CaptchaVerificationException() {
    super("Captcha verification failed");
  }
}

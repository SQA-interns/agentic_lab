package si.konferenca.registration.application;

/** Port: anti-automation check of a reCAPTCHA token (SR-01, 02_contracts/recaptcha.md). */
public interface CaptchaVerifier {

  /** True only when the token is confirmed as valid; any failure returns false (fail closed). */
  boolean verify(String token, String clientAddress);
}

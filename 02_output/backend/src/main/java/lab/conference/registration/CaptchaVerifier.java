package lab.conference.registration;

/** Server-side captcha verification (SR-01). */
public interface CaptchaVerifier {

  /** Mode reported to the frontend: "stub" or "recaptcha". */
  String mode();

  /** Public site key for the browser widget, or null. */
  String siteKey();

  /** True only when the token is verified; any error means false (fail closed). */
  boolean verify(String token, String remoteAddress);
}

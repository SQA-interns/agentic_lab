package si.konferenca.registration.application;

/** Port: the anti-automation check on the trusted side (SR-01). */
public interface CaptchaVerifier {

  /** True only when the token is verified; any error counts as a failed check. */
  boolean verify(String token, String clientAddress);
}

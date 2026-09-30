package si.konferenca.registration.application;

/** Port: verifies an anti-automation token on the backend (SR-01). */
public interface CaptchaVerifier {

  /** True only when the token is confirmed valid; errors and timeouts count as invalid. */
  boolean verify(String token);

  /** True when the deterministic test mode is active (SR-02). */
  boolean testMode();
}

package si.konferenca.registration.application;

/** Port: verifies an anti-automation token on the trusted side (SR-01). */
public interface CaptchaVerifier {

  /**
   * Returns true if the token is valid.
   *
   * @throws ServiceUnavailableException if the verification service cannot be reached
   */
  boolean verify(String token, String clientAddress);

  /** True if the deterministic test mode is on (SR-02). */
  boolean testMode();

  /** The public site key for the form (AR-07); empty in test mode. */
  String siteKey();
}

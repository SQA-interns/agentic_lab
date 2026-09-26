package si.konferenca.registration.application.port;

/** Verifies the anti-automation token submitted with a registration. */
public interface CaptchaVerifier {

  /**
   * @throws si.konferenca.registration.application.CaptchaFailedException if the token is rejected
   * @throws si.konferenca.registration.application.CaptchaUnavailableException if verification
   *     cannot be performed
   */
  void verify(String token, String clientIp);
}

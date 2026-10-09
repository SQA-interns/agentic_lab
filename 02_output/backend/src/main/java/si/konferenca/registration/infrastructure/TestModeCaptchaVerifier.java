package si.konferenca.registration.infrastructure;

import si.konferenca.registration.application.CaptchaVerifier;

/**
 * Deterministic anti-automation for local and test environments (SR-02): accepts exactly the token
 * {@value #VALID_TOKEN} and never calls Google.
 */
public class TestModeCaptchaVerifier implements CaptchaVerifier {

  public static final String VALID_TOKEN = "test-valid";

  @Override
  public void verify(String token, String clientIp) {
    if (!VALID_TOKEN.equals(token)) {
      throw new CaptchaFailedException();
    }
  }
}

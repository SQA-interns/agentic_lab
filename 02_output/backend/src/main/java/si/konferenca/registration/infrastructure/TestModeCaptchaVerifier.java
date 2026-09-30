package si.konferenca.registration.infrastructure;

import si.konferenca.registration.application.CaptchaVerifier;

/**
 * Deterministic reCAPTCHA test mode (SR-02): no call to Google; only the fixed pass token is
 * accepted. Enabled only by configuration.
 */
public class TestModeCaptchaVerifier implements CaptchaVerifier {

  public static final String PASS_TOKEN = "test-mode-pass";

  @Override
  public boolean verify(String token, String clientAddress) {
    return PASS_TOKEN.equals(token);
  }
}

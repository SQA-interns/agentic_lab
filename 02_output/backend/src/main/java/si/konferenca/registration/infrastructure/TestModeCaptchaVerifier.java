package si.konferenca.registration.infrastructure;

import si.konferenca.registration.application.CaptchaVerifier;

/**
 * Deterministic test mode (SR-02): accepts exactly {@value #TOKEN}, makes no network call. Only
 * enabled by configuration and never in production (startup check S-1).
 */
public class TestModeCaptchaVerifier implements CaptchaVerifier {

  public static final String TOKEN = "test-mode-token";

  @Override
  public boolean verify(String token) {
    return TOKEN.equals(token);
  }

  @Override
  public boolean testMode() {
    return true;
  }
}

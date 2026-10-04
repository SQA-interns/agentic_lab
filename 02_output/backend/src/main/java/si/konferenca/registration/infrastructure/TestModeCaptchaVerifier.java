package si.konferenca.registration.infrastructure;

import si.konferenca.registration.application.CaptchaVerifier;

/**
 * Deterministic test mode (SR-02): no call to Google; only the token "test-pass" passes. Allowed
 * only in the local and test environments (startup guards).
 */
public final class TestModeCaptchaVerifier implements CaptchaVerifier {

  public static final String PASSING_TOKEN = "test-pass";

  @Override
  public boolean verify(String token, String clientAddress) {
    return PASSING_TOKEN.equals(token);
  }
}

package si.konferenca.registration.adapter.out.captcha;

import si.konferenca.registration.domain.CaptchaVerifier;

/**
 * Deterministic test mode (recaptcha-verify.schema.json, testMode): no network call; exactly one
 * token passes. Used only when the test mode is switched on by configuration (SR-02).
 */
public class TestModeCaptchaVerifier implements CaptchaVerifier {

  public static final String PASS_TOKEN = "test-pass";

  @Override
  public boolean verify(String token) {
    return PASS_TOKEN.equals(token);
  }
}

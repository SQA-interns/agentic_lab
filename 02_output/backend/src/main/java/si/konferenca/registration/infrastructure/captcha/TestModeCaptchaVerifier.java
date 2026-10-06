package si.konferenca.registration.infrastructure.captcha;

import si.konferenca.registration.application.RegistrationPorts.CaptchaResult;
import si.konferenca.registration.application.RegistrationPorts.CaptchaVerifier;

/**
 * Deterministic test mode (SR-02), enabled only by configuration in local and test: the token
 * {@code test-pass} passes, every other token fails, and nothing leaves the machine.
 */
public class TestModeCaptchaVerifier implements CaptchaVerifier {

  public static final String PASSING_TOKEN = "test-pass";

  @Override
  public CaptchaResult verify(String token) {
    return PASSING_TOKEN.equals(token) ? CaptchaResult.PASSED : CaptchaResult.FAILED;
  }
}

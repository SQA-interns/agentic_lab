package si.konferenca.registration.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import si.konferenca.registration.service.CaptchaVerifier;

/**
 * Deterministic captcha verifier for local development and automated tests. Accepts exactly {@link
 * #TEST_TOKEN}; never calls Google. Active only when {@code app.recaptcha.test-mode=true}.
 */
@Component
@ConditionalOnProperty(name = "app.recaptcha.test-mode", havingValue = "true")
public class TestModeCaptchaVerifier implements CaptchaVerifier {

  public static final String TEST_TOKEN = "test-mode-pass";

  private static final Logger LOG = LoggerFactory.getLogger(TestModeCaptchaVerifier.class);

  public TestModeCaptchaVerifier() {
    LOG.warn("reCAPTCHA TEST MODE is active - never enable this in production");
  }

  @Override
  public boolean verify(String token, String remoteIp) {
    return TEST_TOKEN.equals(token);
  }

  @Override
  public CaptchaSettings settings() {
    return new CaptchaSettings(CaptchaSettings.Mode.TEST, null);
  }
}

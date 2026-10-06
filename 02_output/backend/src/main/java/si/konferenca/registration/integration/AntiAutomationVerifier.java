package si.konferenca.registration.integration;

import org.springframework.stereotype.Component;
import si.konferenca.registration.config.AppProperties;

/**
 * Verifies the anti-automation token on the trusted side (SR-01). In test mode (SR-02, only in
 * local and test environments) only {@value #TEST_MODE_TOKEN} passes and nothing is called.
 */
@Component
public class AntiAutomationVerifier {

  /** The deterministic test-mode token (`recaptcha-siteverify.openapi.yaml`). */
  public static final String TEST_MODE_TOKEN = "test-mode-pass";

  private static final int MAX_TOKEN_LENGTH = 4096;

  /** Result of a verification. */
  public enum Outcome {
    PASSED,
    FAILED,
    UNAVAILABLE
  }

  private final AppProperties.Recaptcha settings;

  public AntiAutomationVerifier(AppProperties app) {
    this.settings = app.recaptcha();
  }

  public Outcome verify(String token, String remoteAddress) {
    if (token == null || token.isBlank() || token.length() > MAX_TOKEN_LENGTH) {
      return Outcome.FAILED;
    }
    if (settings.testMode()) {
      return TEST_MODE_TOKEN.equals(token) ? Outcome.PASSED : Outcome.FAILED;
    }
    return Outcome.FAILED;
  }
}

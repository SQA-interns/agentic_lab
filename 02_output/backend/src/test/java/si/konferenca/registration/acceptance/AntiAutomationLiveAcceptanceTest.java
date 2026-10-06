package si.konferenca.registration.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static si.konferenca.registration.acceptance.support.Payloads.external;
import static si.konferenca.registration.acceptance.support.Payloads.student;
import static si.konferenca.registration.acceptance.support.Payloads.with;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import si.konferenca.registration.acceptance.support.AcceptanceTestBase;
import si.konferenca.registration.acceptance.support.MockVerificationServer;

/**
 * Anti-automation with test mode off: the production verification path against a mocked
 * verification endpoint (SR-01, DoD-P05).
 */
class AntiAutomationLiveAcceptanceTest extends AcceptanceTestBase {

  private static final String SECRET = "acceptance-secret-key";

  // D-21: bound property names, because the base class's environment-style keys win over a
  // subclass registering the same keys.
  @DynamicPropertySource
  static void liveVerification(DynamicPropertyRegistry registry) {
    registry.add("app.recaptcha.test-mode", () -> "false");
    registry.add("app.recaptcha.site-key", () -> "acceptance-site-key");
    registry.add("app.recaptcha.secret-key", () -> SECRET);
    registry.add("app.recaptcha.verify-url", MockVerificationServer::url);
  }

  @BeforeEach
  void resetMock() {
    MockVerificationServer.reset();
  }

  @Test
  void AC_001_13_token_confirmed_by_the_verification_service_is_accepted() {
    assertAccepted(
        api.register(with(external(), "antiAutomationToken", MockVerificationServer.VALID_TOKEN)));

    assertThat(MockVerificationServer.requests()).hasSize(1);
    Map<String, String> request = MockVerificationServer.requests().get(0);
    assertThat(request.get("secret")).isEqualTo(SECRET);
    assertThat(request.get("response")).isEqualTo(MockVerificationServer.VALID_TOKEN);
  }

  @Test
  void AC_001_13_token_rejected_by_the_verification_service_is_rejected() {
    assertError(
        api.register(with(external(), "antiAutomationToken", "forged-token")),
        400,
        "CAPTCHA_FAILED");

    assertThat(MockVerificationServer.requests()).hasSize(1);
    assertNothingStored();
  }

  @Test
  void AC_001_13_test_mode_token_is_rejected_when_test_mode_is_off() {
    assertError(
        api.register(with(external(), "antiAutomationToken", "test-mode-pass")),
        400,
        "CAPTCHA_FAILED");
    assertNothingStored();
  }

  @Test
  void AC_001_13_unavailable_verification_service_rejects_the_registration() {
    MockVerificationServer.answerWith(MockVerificationServer.Mode.SERVER_ERROR);

    assertError(
        api.register(with(external(), "antiAutomationToken", MockVerificationServer.VALID_TOKEN)),
        503,
        "CAPTCHA_UNAVAILABLE");
    assertNothingStored();
  }

  @Test
  void AC_002_11_student_token_rejected_by_the_verification_service_is_rejected() {
    assertError(
        api.register(with(student(), "antiAutomationToken", "forged-token")),
        400,
        "CAPTCHA_FAILED");
    assertNothingStored();
  }
}

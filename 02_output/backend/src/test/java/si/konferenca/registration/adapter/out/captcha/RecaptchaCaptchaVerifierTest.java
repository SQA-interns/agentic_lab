package si.konferenca.registration.adapter.out.captcha;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import si.konferenca.registration.domain.CaptchaVerifier.CaptchaUnavailableException;
import si.konferenca.registration.integration.MockVerificationEndpoint;

/** The reCAPTCHA verification call against a mocked endpoint (SR-01, DoD-P05). */
class RecaptchaCaptchaVerifierTest {

  private static final String SECRET = "secret key/with+special&chars";

  private final MockVerificationEndpoint endpoint = new MockVerificationEndpoint();
  private final RecaptchaCaptchaVerifier verifier =
      new RecaptchaCaptchaVerifier(endpoint.url(), SECRET);

  @AfterEach
  void stopEndpoint() {
    endpoint.close();
  }

  @Test
  void sr01_acceptedTokenPassesAndOnlySecretAndTokenAreSent() {
    endpoint.answer(200, "{\"success\":true,\"hostname\":\"konferenca.example\"}");

    assertThat(verifier.verify("token value&=")).isTrue();

    assertThat(endpoint.contentTypes()).containsExactly("application/x-www-form-urlencoded");
    // Form encoded; no remoteip or any other field (SB-12).
    assertThat(endpoint.requestBodies())
        .containsExactly("secret=secret+key%2Fwith%2Bspecial%26chars&response=token+value%26%3D");
  }

  @Test
  void sr01_rejectedTokenFails() {
    endpoint.answer(200, "{\"success\":false,\"error-codes\":[\"invalid-input-response\"]}");

    assertThat(verifier.verify("forged")).isFalse();
  }

  @ParameterizedTest
  @ValueSource(ints = {400, 403, 500, 503})
  void errorStatusIsNotASuccessButUnavailable(int status) {
    endpoint.answer(status, "{\"success\":true}");

    assertThatThrownBy(() -> verifier.verify("token"))
        .isInstanceOf(CaptchaUnavailableException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "not json", "{}", "{\"success\":\"true\"}", "{\"success\":1}", "[]"})
  void answerWithoutABooleanSuccessFlagIsUnavailable(String body) {
    endpoint.answer(200, body);

    assertThatThrownBy(() -> verifier.verify("token"))
        .isInstanceOf(CaptchaUnavailableException.class);
  }

  @Test
  void unreachableEndpointIsUnavailableAndTheSecretIsNotInTheMessage() {
    endpoint.close();

    assertThatThrownBy(() -> verifier.verify("token"))
        .isInstanceOf(CaptchaUnavailableException.class)
        .hasMessageNotContaining(SECRET);
  }

  @Test
  void everySuccessStatusUpTo299IsRead() {
    endpoint.answer(299, "{\"success\":true}");

    assertThat(verifier.verify("token")).isTrue();
  }

  @Test
  void sr02_testModeAcceptsExactlyThePassingToken() {
    TestModeCaptchaVerifier testMode = new TestModeCaptchaVerifier();

    assertThat(testMode.verify("test-pass")).isTrue();
    assertThat(testMode.verify("test-pass ")).isFalse();
    assertThat(testMode.verify("")).isFalse();
    assertThat(testMode.verify(null)).isFalse();
  }
}

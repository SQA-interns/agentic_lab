package si.konferenca.registration.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static si.konferenca.registration.acceptance.support.Problems.assertFieldError;
import static si.konferenca.registration.acceptance.support.Registrations.external;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import si.konferenca.registration.acceptance.support.AcceptanceTestBase;
import si.konferenca.registration.acceptance.support.Api;
import si.konferenca.registration.acceptance.support.CaptchaMock;
import si.konferenca.registration.acceptance.support.Database;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * US-001 anti-automation in live mode (SR-01, DoD-P05): the production verification path against a
 * mocked verification endpoint, with accepted, rejected and unavailable answers.
 */
class Us001LiveCaptchaAcceptanceTest extends AcceptanceTestBase {

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    configure(
        registry,
        Map.of(
            "RECAPTCHA_TEST_MODE",
            "false",
            "RECAPTCHA_SITE_KEY",
            CaptchaMock.SITE_KEY,
            "RECAPTCHA_SECRET_KEY",
            CaptchaMock.SECRET,
            "RECAPTCHA_VERIFY_URL",
            CaptchaMock.verifyUrl()));
  }

  @BeforeEach
  void clearMock() {
    CaptchaMock.clear();
  }

  private static ObjectNode withToken(String token) {
    ObjectNode request = external();
    request.put("captchaToken", token);
    return request;
  }

  @Test
  void ac001_02_tokenConfirmedByTheVerificationServiceIsAccepted() {
    Api.Response response = api.register(withToken(CaptchaMock.ACCEPTED));

    assertThat(response.status()).as("body: %s", response.text()).isEqualTo(201);
    assertThat(CaptchaMock.requests()).hasSize(1);
    assertThat(CaptchaMock.requests().get(0))
        .containsEntry("secret", CaptchaMock.SECRET)
        .containsEntry("response", CaptchaMock.ACCEPTED);
    assertThat(Database.registrationCount()).isEqualTo(1);
  }

  @Test
  void ac001_14_tokenRejectedByTheVerificationServiceIsRefused() {
    Api.Response response = api.register(withToken(CaptchaMock.REJECTED));

    assertFieldError(response, 400, "captchaToken", "CAPTCHA_FAILED");
    assertThat(CaptchaMock.requests()).hasSize(1);
    assertNothingStored();
  }

  @Test
  void ac001_14_testModeTokenIsRefusedInLiveMode() {
    Api.Response response = api.register(withToken("test-pass"));

    assertFieldError(response, 400, "captchaToken", "CAPTCHA_FAILED");
    assertNothingStored();
  }

  @Test
  void ac001_14_unavailableVerificationServiceRefusesTheRegistration() {
    Api.Response response = api.register(withToken(CaptchaMock.OUTAGE));

    assertThat(response.status()).as("body: %s", response.text()).isEqualTo(503);
    assertNothingStored();
  }

  @Test
  void ac001_14_liveModePublishesTheSiteKeyForTheWidget() {
    Api.Response response = api.getFormConfig();

    assertThat(response.status()).as("body: %s", response.text()).isEqualTo(200);
    JsonNode captcha = response.json().path("captcha");
    assertThat(captcha.path("mode").asString()).isEqualTo("live");
    assertThat(captcha.path("siteKey").asString()).isEqualTo(CaptchaMock.SITE_KEY);
    assertThat(response.text()).doesNotContain(CaptchaMock.SECRET);
  }
}

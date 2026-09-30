package si.konferenca.registration.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static si.konferenca.registration.acceptance.support.Checks.assertFieldError;
import static si.konferenca.registration.acceptance.support.Checks.assertNothingStored;
import static si.konferenca.registration.acceptance.support.Payloads.external;
import static si.konferenca.registration.acceptance.support.Payloads.with;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import si.konferenca.registration.acceptance.support.AcceptanceTest;
import si.konferenca.registration.acceptance.support.Api;
import si.konferenca.registration.acceptance.support.CustomContextTest;
import si.konferenca.registration.acceptance.support.Database;
import si.konferenca.registration.acceptance.support.RecaptchaMock;
import si.konferenca.registration.acceptance.support.TestEnvironment;

/** US-001 / SR-01 / DoD-P05: the production reCAPTCHA path against a mocked endpoint. */
class RecaptchaVerificationAcceptanceTest extends CustomContextTest {

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    Map<String, String> p = TestEnvironment.baseProperties();
    p.put("app.recaptcha.test-mode", "false");
    p.put("app.recaptcha.site-key", RecaptchaMock.SITE_KEY);
    p.put("app.recaptcha.secret-key", RecaptchaMock.SECRET);
    p.put("app.recaptcha.verify-url", RecaptchaMock.get().url());
    AcceptanceTest.register(registry, p);
  }

  @Test
  @DisplayName("AC-001-08 a token the verification endpoint accepts gives 201")
  void ac001_08_acceptsVerifiedToken() {
    Map<String, Object> payload = with(external(), "recaptchaToken", RecaptchaMock.GOOD_TOKEN);
    String email = (String) payload.get("email");
    int before = RecaptchaMock.get().requests().size();

    Api.Response response = api.register(payload, "10.200.0.1");

    assertThat(response.status()).as(response.text()).isEqualTo(201);
    assertThat(Database.countByEmail(email)).isEqualTo(1);
    assertThat(RecaptchaMock.get().requests()).hasSizeGreaterThan(before);
    Map<String, String> call = RecaptchaMock.get().requests().get(before);
    assertThat(call.get("_method")).isEqualTo("POST");
    assertThat(call.get("_contentType")).startsWith("application/x-www-form-urlencoded");
    assertThat(call.get("secret")).isEqualTo(RecaptchaMock.SECRET);
    assertThat(call.get("response")).isEqualTo(RecaptchaMock.GOOD_TOKEN);
    assertThat(call.get("remoteip")).isEqualTo("10.200.0.1");
  }

  @Test
  @DisplayName("AC-001-08 a token the verification endpoint rejects gives 400 and stores nothing")
  void ac001_08_rejectsTokenRefusedByEndpoint() {
    Map<String, Object> payload = with(external(), "recaptchaToken", "bot-token");
    String email = (String) payload.get("email");
    int before = RecaptchaMock.get().requests().size();

    Api.Response response = api.register(payload);

    assertFieldError(response, 400, "recaptchaToken");
    assertThat(RecaptchaMock.get().requests()).hasSizeGreaterThan(before);
    assertNothingStored(email, TestEnvironment.defaultJsonDir());
  }

  @Test
  @DisplayName("AC-001-08 a failing verification endpoint gives 400 and stores nothing")
  void ac001_08_rejectsWhenEndpointFails() {
    Map<String, Object> payload =
        with(external(), "recaptchaToken", RecaptchaMock.SERVER_ERROR_TOKEN);
    String email = (String) payload.get("email");

    Api.Response response = api.register(payload);

    assertFieldError(response, 400, "recaptchaToken");
    assertNothingStored(email, TestEnvironment.defaultJsonDir());
  }

  @Test
  @DisplayName("AC-001-08 the test-mode token is not accepted when test mode is off")
  void ac001_08_rejectsTestModeTokenOutsideTestMode() {
    Map<String, Object> payload =
        with(external(), "recaptchaToken", TestEnvironment.TEST_MODE_TOKEN);
    String email = (String) payload.get("email");

    Api.Response response = api.register(payload);

    assertFieldError(response, 400, "recaptchaToken");
    assertNothingStored(email, TestEnvironment.defaultJsonDir());
  }

  @Test
  @DisplayName("AC-001-08 the client configuration reports test mode off and the site key")
  void ac001_08_clientConfigShowsProductionMode() {
    Api.Response response = api.get("/api/config");

    assertThat(response.status()).isEqualTo(200);
    assertThat(response.json().get("recaptchaTestMode").asBoolean()).isFalse();
    assertThat(response.json().get("recaptchaSiteKey").asString())
        .isEqualTo(RecaptchaMock.SITE_KEY);
    assertThat(response.text()).doesNotContain(RecaptchaMock.SECRET);
  }
}

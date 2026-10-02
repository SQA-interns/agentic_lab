package si.konferenca.registration.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * DoD-P05: the production anti-automation path (test mode off) through the API, against a mocked
 * verification endpoint, including a rejected token (SR-01, AC-001-12).
 */
class RecaptchaIntegrationTest {

  private static MockVerificationEndpoint endpoint;
  private static IntegrationApp app;

  @BeforeAll
  static void start() {
    endpoint = new MockVerificationEndpoint();
    app =
        IntegrationApp.start(
            Map.of(
                "RECAPTCHA_TEST_MODE", "false",
                "RECAPTCHA_SITE_KEY", "site-key-for-the-widget",
                "RECAPTCHA_SECRET_KEY", "secret-key-for-verification",
                "RECAPTCHA_VERIFY_URL", endpoint.url()));
  }

  @AfterAll
  static void stop() {
    app.close();
    endpoint.close();
  }

  @Test
  void ar07_formConfigurationOffersTheSiteKeyAndNeverTheSecret() {
    HttpResponse<String> reply = app.get("/api/form-config");

    assertThat(reply.statusCode()).isEqualTo(200);
    assertThat(reply.body())
        .contains("\"mode\":\"recaptcha\"", "\"siteKey\":\"site-key-for-the-widget\"")
        .doesNotContain("secret-key-for-verification");
  }

  @Test
  void ac_001_12_tokenAcceptedByTheVerificationEndpointIsAccepted() {
    endpoint.answer(200, "{\"success\":true}");
    int before = IntegrationApp.registrationCount();

    HttpResponse<String> reply = app.register("token-from-the-widget");

    assertThat(reply.statusCode()).as(reply.body()).isEqualTo(201);
    assertThat(IntegrationApp.registrationCount()).isEqualTo(before + 1);
    assertThat(endpoint.requestBodies())
        .contains("secret=secret-key-for-verification&response=token-from-the-widget");
  }

  @Test
  void ac_001_12_tokenRejectedByTheVerificationEndpointIsRejected() {
    endpoint.answer(200, "{\"success\":false,\"error-codes\":[\"invalid-input-response\"]}");
    int before = IntegrationApp.registrationCount();

    HttpResponse<String> reply = app.register("forged-token");

    assertThat(reply.statusCode()).isEqualTo(400);
    assertThat(reply.body()).contains("\"field\":\"captchaToken\"", "\"code\":\"captcha_failed\"");
    assertThat(IntegrationApp.registrationCount()).isEqualTo(before);
  }

  @Test
  void sr01_testModeTokenDoesNotPassInProductionMode() {
    endpoint.answer(200, "{\"success\":false}");

    assertThat(app.register("test-pass").statusCode()).isEqualTo(400);
  }

  @Test
  void sr01_failingVerificationEndpointMeansNotReceivedAndNothingStored() {
    endpoint.answer(500, "{\"success\":true}");
    int before = IntegrationApp.registrationCount();

    HttpResponse<String> reply = app.register("token-from-the-widget");

    assertThat(reply.statusCode()).isEqualTo(503);
    assertThat(reply.headers().firstValue("Content-Type").orElse(""))
        .startsWith("application/problem+json");
    assertThat(reply.body()).doesNotContain("Exception", "siteverify", "secret-key");
    assertThat(IntegrationApp.registrationCount()).isEqualTo(before);
  }

  @Test
  void sr01_invalidInputIsRejectedWithoutAVerificationCall() {
    int callsBefore = endpoint.requestBodies().size();

    HttpResponse<String> reply =
        app.post(
            "/api/registrations",
            "application/json",
            "{\"type\":\"EXTERNAL\",\"captchaToken\":\"token\",\"consent\":true,\"optionIds\":[]}");

    assertThat(reply.statusCode()).isEqualTo(400);
    assertThat(endpoint.requestBodies()).hasSize(callsBefore);
  }
}

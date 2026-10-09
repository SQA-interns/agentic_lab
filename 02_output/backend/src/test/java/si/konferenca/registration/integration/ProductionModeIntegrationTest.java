package si.konferenca.registration.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import si.konferenca.registration.acceptance.support.ApiClient;
import si.konferenca.registration.acceptance.support.AppInstance;
import si.konferenca.registration.acceptance.support.Registrations;
import si.konferenca.registration.acceptance.support.TestStack;

/**
 * The application with reCAPTCHA test mode off, against a mocked verification endpoint (DoD-P05,
 * SR-01, SR-02), and with a low registration rate limit (SR-03).
 */
class ProductionModeIntegrationTest {

  private HttpServer verifyStub;
  private final List<String> tokensSeen = new CopyOnWriteArrayList<>();

  @BeforeEach
  void startVerifyStub() throws Exception {
    verifyStub = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    verifyStub.createContext(
        "/siteverify",
        exchange -> {
          String form =
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
          tokensSeen.add(form);
          byte[] reply =
              (form.contains("response=human") ? "{\"success\":true}" : "{\"success\":false}")
                  .getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, reply.length);
          exchange.getResponseBody().write(reply);
          exchange.close();
        });
    verifyStub.start();
    TestStack.database().clear();
  }

  @AfterEach
  void stopVerifyStub() {
    verifyStub.stop(0);
  }

  private Map<String, Object> productionSettings() {
    return Map.of(
        "RECAPTCHA_TEST_MODE", "false",
        "RECAPTCHA_SITE_KEY", "site-key-123",
        "RECAPTCHA_SECRET_KEY", "secret-key-456",
        "RECAPTCHA_VERIFY_URL",
            "http://127.0.0.1:" + verifyStub.getAddress().getPort() + "/siteverify",
        "RATE_LIMIT_REGISTRATIONS_PER_MINUTE", "3");
  }

  @Test
  void googleVerifiedTokensAreAcceptedAndRejectedOnesRefused() {
    try (AppInstance app = AppInstance.start(productionSettings())) {
      ApiClient api = app.api();

      ApiClient.Response form = api.getRegistrationForm();
      assertThat(form.json().path("captcha").path("mode").asString()).isEqualTo("recaptcha");
      assertThat(form.json().path("captcha").path("siteKey").asString()).isEqualTo("site-key-123");
      assertThat(form.text()).doesNotContain("secret-key-456");

      var human = Registrations.external();
      human.put("captchaToken", "human");
      assertThat(api.register(human).status()).isEqualTo(201);

      var bot = Registrations.external();
      bot.put("captchaToken", "test-valid");
      ApiClient.Response rejected = api.register(bot);
      assertThat(rejected.status()).isEqualTo(400);
      assertThat(rejected.json().path("error").asString()).isEqualTo("captcha_failed");

      assertThat(tokensSeen)
          .anyMatch(f -> f.contains("secret=secret-key-456") && f.contains("response=human"));

      var third = Registrations.external();
      third.put("captchaToken", "human");
      assertThat(api.register(third).status()).isEqualTo(201);
      ApiClient.Response limited = api.register(Registrations.external());
      assertThat(limited.status()).isEqualTo(429);
      assertThat(limited.header("Retry-After")).isNotBlank();
    }
  }

  @Test
  void unreachableVerificationGivesServiceUnavailable() {
    verifyStub.stop(0);
    try (AppInstance app = AppInstance.start(productionSettings())) {
      var request = Registrations.external();
      request.put("captchaToken", "human");

      ApiClient.Response response = app.api().register(request);

      assertThat(response.status()).isEqualTo(503);
      assertThat(response.json().path("error").asString()).isEqualTo("captcha_unavailable");
    }
  }

  @Test
  void missingKeysWithoutTestModePreventStartup() {
    Throwable failure =
        catchThrowable(() -> AppInstance.start(Map.of("RECAPTCHA_TEST_MODE", "false")).close());

    assertThat(failure).isNotNull();
    StringBuilder chain = new StringBuilder();
    for (Throwable t = failure; t != null; t = t.getCause()) {
      chain.append(t.getMessage());
    }
    assertThat(chain.toString()).contains("RECAPTCHA_SITE_KEY");
  }
}

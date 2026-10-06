package si.konferenca.registration.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static si.konferenca.registration.acceptance.support.Registrations.external;
import static si.konferenca.registration.acceptance.support.Registrations.with;

import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import si.konferenca.registration.acceptance.support.AcceptanceEnvironment;
import si.konferenca.registration.acceptance.support.RunningApp;
import si.konferenca.registration.acceptance.support.RunningApp.Response;

/**
 * AC-001-14 with the production reCAPTCHA code path (test mode off) against a stubbed verification
 * endpoint (recaptcha.schema.json, DoD-P05). No call reaches Google.
 */
class AntiAutomationAcceptanceTest {

  private static final String SECRET = "stub-secret-key";
  private static final List<String> REQUESTS = new CopyOnWriteArrayList<>();
  private static HttpServer stub;
  private static RunningApp app;

  @BeforeAll
  static void start() throws Exception {
    stub = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    stub.createContext(
        "/siteverify",
        exchange -> {
          String form =
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
          REQUESTS.add(URLDecoder.decode(form, StandardCharsets.UTF_8));
          boolean ok = form.contains("response=google-ok");
          byte[] body =
              (ok
                      ? "{\"success\": true, \"hostname\": \"localhost\"}"
                      : "{\"success\": false, \"error-codes\": [\"invalid-input-response\"]}")
                  .getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, body.length);
          try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
          }
        });
    stub.start();
    Map<String, String> config = AcceptanceEnvironment.defaultConfiguration();
    config.put("RECAPTCHA_TEST_MODE", "false");
    config.put("RECAPTCHA_SITE_KEY", "stub-site-key");
    config.put("RECAPTCHA_SECRET_KEY", SECRET);
    config.put(
        "RECAPTCHA_VERIFY_URL", "http://127.0.0.1:" + stub.getAddress().getPort() + "/siteverify");
    app = RunningApp.start(config);
  }

  @AfterAll
  static void stop() {
    app.close();
    stub.stop(0);
  }

  @Test
  void AC_001_14_tokenRejectedByVerificationServiceIsRejectedAndNothingStored() {
    Map<String, Object> reg = with(external(), "recaptchaToken", "google-bad");

    Response r = app.register(reg);

    assertThat(r.status()).isEqualTo(400);
    assertThat(r.fieldErrors()).containsEntry("recaptchaToken", "RECAPTCHA_FAILED");
    assertThat(app.registrationRows((String) reg.get("email"))).isEmpty();
    assertThat(REQUESTS).anySatisfy(q -> assertThat(q).contains("response=google-bad"));
  }

  @Test
  void AC_001_14_tokenConfirmedByVerificationServiceIsAccepted() {
    Map<String, Object> reg = with(external(), "recaptchaToken", "google-ok");

    Response r = app.register(reg);

    assertThat(r.status()).isEqualTo(201);
    assertThat(REQUESTS)
        .anySatisfy(q -> assertThat(q).contains("secret=" + SECRET).contains("response=google-ok"));
  }

  @Test
  void AC_001_14_testModeTokenIsNotAcceptedWhenTestModeIsOff() {
    Map<String, Object> reg = with(external(), "recaptchaToken", "test-pass");

    Response r = app.register(reg);

    assertThat(r.status()).isEqualTo(400);
    assertThat(r.fieldErrors()).containsEntry("recaptchaToken", "RECAPTCHA_FAILED");
  }

  @Test
  void AC_001_14_formSetupOffersSiteKeyAndNoTestMode() {
    Response r = app.get("/api/options");

    assertThat(r.status()).isEqualTo(200);
    assertThat(r.json().path("recaptcha").path("testMode").asBoolean()).isFalse();
    assertThat(r.json().path("recaptcha").path("siteKey").asString()).isEqualTo("stub-site-key");
  }
}

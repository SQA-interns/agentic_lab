package si.konferenca.registration.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import si.konferenca.registration.RegistrationApplication;
import si.konferenca.registration.acceptance.support.Api;
import si.konferenca.registration.acceptance.support.Db;
import si.konferenca.registration.acceptance.support.Fixtures;
import si.konferenca.registration.acceptance.support.TestInfrastructure;

/**
 * SR-01, DoD-P05: the production reCAPTCHA code path, against a mocked verification endpoint,
 * including a rejected token.
 */
@SpringBootTest(
    classes = RegistrationApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CaptchaVerificationAcceptanceTest {

  private static final String SECRET = "mock-recaptcha-secret";
  private static final String SITE_KEY = "mock-recaptcha-site-key";
  private static final String VALID_TOKEN = "valid-token";
  private static final List<Map<String, String>> VERIFY_CALLS = new CopyOnWriteArrayList<>();
  private static final HttpServer VERIFY_SERVER = startVerifyServer();

  @LocalServerPort int port;

  private Api api;
  private final Db db = TestInfrastructure.db();

  @DynamicPropertySource
  static void configuration(DynamicPropertyRegistry registry) {
    TestInfrastructure.register(
        registry,
        Map.of(
            "app.recaptcha.test-mode",
            "false",
            "app.recaptcha.site-key",
            SITE_KEY,
            "app.recaptcha.secret-key",
            SECRET,
            "app.recaptcha.verify-url",
            "http://127.0.0.1:" + VERIFY_SERVER.getAddress().getPort() + "/siteverify"));
  }

  @BeforeEach
  void setUp() {
    api = new Api(port);
    VERIFY_CALLS.clear();
  }

  @AfterAll
  static void stopServer() {
    VERIFY_SERVER.stop(0);
  }

  @Test
  @DisplayName("SR-01 AC-001-01 a token the verification service accepts lets the registration in")
  void sr01ValidTokenIsVerifiedOnTheBackend() {
    String email = Fixtures.uniqueEmail();
    Map<String, Object> request = Fixtures.external(email);
    request.put("captchaToken", VALID_TOKEN);

    Api.Response r = api.register(request);

    assertThat(r.status()).as(r.text()).isEqualTo(201);
    assertThat(VERIFY_CALLS).hasSize(1);
    assertThat(VERIFY_CALLS.get(0)).containsEntry("secret", SECRET);
    assertThat(VERIFY_CALLS.get(0)).containsEntry("response", VALID_TOKEN);
    assertThat(db.countRegistrations(email)).isEqualTo(1);
  }

  @Test
  @DisplayName("SR-01 DoD-P05 a token the verification service rejects is refused")
  void sr01RejectedTokenIsRefused() {
    String email = Fixtures.uniqueEmail();
    Map<String, Object> request = Fixtures.external(email);
    request.put("captchaToken", "forged-token");

    Api.Response r = api.register(request);

    assertThat(r.status()).as(r.text()).isEqualTo(400);
    assertThat(r.errorFields()).contains("captchaToken");
    assertThat(VERIFY_CALLS).hasSize(1);
    assertThat(db.countRegistrations(email)).isZero();
  }

  @Test
  @DisplayName("SR-01 SR-02 the test-mode token is not accepted when test mode is off")
  void sr02TestModeTokenIsRefusedOutsideTestMode() {
    String email = Fixtures.uniqueEmail();

    Api.Response r = api.register(Fixtures.external(email));

    assertThat(r.status()).as(r.text()).isEqualTo(400);
    assertThat(r.errorFields()).contains("captchaToken");
    assertThat(db.countRegistrations(email)).isZero();
  }

  @Test
  @DisplayName("SR-01 a missing token is refused whatever the frontend does")
  void sr01MissingTokenIsRefused() {
    String email = Fixtures.uniqueEmail();
    Map<String, Object> request = Fixtures.external(email);
    request.put("captchaToken", "");

    Api.Response r = api.register(request);

    assertThat(r.status()).as(r.text()).isEqualTo(400);
    assertThat(r.errorFields()).contains("captchaToken");
    assertThat(db.countRegistrations(email)).isZero();
  }

  @Test
  @DisplayName("AR-07 SR-02 the public client configuration exposes only the site key")
  void ar07ClientConfigurationExposesSiteKeyOnly() {
    Api.Response r = api.get("/api/config");

    assertThat(r.status()).as(r.text()).isEqualTo(200);
    assertThat(r.json().path("recaptchaSiteKey").asString()).isEqualTo(SITE_KEY);
    assertThat(r.json().path("captchaTestMode").asBoolean()).isFalse();
    assertThat(r.text()).doesNotContain(SECRET);
  }

  private static HttpServer startVerifyServer() {
    try {
      HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext(
          "/siteverify",
          exchange -> {
            String form =
                new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, String> params = new HashMap<>();
            for (String pair : form.split("&")) {
              String[] kv = pair.split("=", 2);
              if (kv.length == 2) {
                params.put(
                    URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
                    URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
              }
            }
            String query = exchange.getRequestURI().getRawQuery();
            if (query != null) {
              for (String pair : query.split("&")) {
                String[] kv = pair.split("=", 2);
                if (kv.length == 2) {
                  params.putIfAbsent(
                      URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
                      URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
                }
              }
            }
            VERIFY_CALLS.add(params);
            boolean ok =
                SECRET.equals(params.get("secret")) && VALID_TOKEN.equals(params.get("response"));
            byte[] body =
                (ok
                        ? "{\"success\": true, \"hostname\": \"localhost\"}"
                        : "{\"success\": false, \"error-codes\": [\"invalid-input-response\"]}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
              os.write(body);
            }
          });
      server.start();
      return server;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}

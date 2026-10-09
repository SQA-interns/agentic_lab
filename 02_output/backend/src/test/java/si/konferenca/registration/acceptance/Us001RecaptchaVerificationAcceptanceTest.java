package si.konferenca.registration.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static si.konferenca.registration.acceptance.support.Api.assertFieldError;
import static si.konferenca.registration.acceptance.support.Api.assertNothingStoredFor;
import static si.konferenca.registration.acceptance.support.Api.exportRowsFor;
import static si.konferenca.registration.acceptance.support.Api.external;
import static si.konferenca.registration.acceptance.support.Api.jsonCopies;
import static si.konferenca.registration.acceptance.support.Api.register;
import static si.konferenca.registration.acceptance.support.Api.uniqueEmail;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import si.konferenca.registration.acceptance.support.Api;
import si.konferenca.registration.acceptance.support.Api.Response;
import si.konferenca.registration.acceptance.support.Backend;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * US-001 anti-automation through the production reCAPTCHA code path, verified against a mocked
 * verification endpoint (SR-01, DoD-P05): only the token "good-token" is accepted by the mock.
 */
class Us001RecaptchaVerificationAcceptanceTest {

  private static final String SECRET = "acceptance-recaptcha-secret";
  private static final List<String> REQUESTS = new CopyOnWriteArrayList<>();
  private static HttpServer verifier;
  private static Backend backend;

  @BeforeAll
  static void start() throws IOException {
    verifier = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    verifier.createContext(
        "/siteverify",
        exchange -> {
          String form =
              URLDecoder.decode(
                  new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8),
                  StandardCharsets.UTF_8);
          REQUESTS.add(form);
          boolean ok = form.contains("response=good-token") && form.contains("secret=" + SECRET);
          byte[] body = ("{\"success\":" + ok + "}").getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, body.length);
          try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
          }
        });
    verifier.start();
    backend =
        Backend.builder()
            .setting("RECAPTCHA_TEST_MODE", "false")
            .setting("RECAPTCHA_SITE_KEY", "acceptance-site-key")
            .setting("RECAPTCHA_SECRET_KEY", SECRET)
            .setting(
                "RECAPTCHA_VERIFY_URL",
                "http://127.0.0.1:" + verifier.getAddress().getPort() + "/siteverify")
            .start();
  }

  @AfterAll
  static void stop() {
    backend.close();
    verifier.stop(0);
  }

  @Test
  void AC_001_16_formServesTheSiteKeyInGoogleMode() {
    JsonNode recaptcha = Api.form(backend, "external").get("recaptcha");

    assertThat(recaptcha.get("mode").asString()).isEqualTo("GOOGLE");
    assertThat(recaptcha.get("siteKey").asString()).isEqualTo("acceptance-site-key");
  }

  @Test
  void AC_001_16_tokenRejectedByTheVerificationServiceIsRejected() {
    String email = uniqueEmail();
    ObjectNode body = external(email);
    body.put("recaptchaToken", "bad-token");
    int copies = jsonCopies(backend).size();
    int calls = REQUESTS.size();

    Response response = register(backend, body);

    assertFieldError(response, 400, "recaptchaToken", "CAPTCHA_FAILED");
    assertThat(REQUESTS.size()).isGreaterThan(calls);
    assertNothingStoredFor(backend, email, copies);
  }

  @Test
  void AC_001_16_testModeTokenIsRejectedOutsideTestMode() {
    String email = uniqueEmail();
    ObjectNode body = external(email);
    body.put("recaptchaToken", Api.TEST_CAPTCHA_TOKEN);
    int copies = jsonCopies(backend).size();

    Response response = register(backend, body);

    assertFieldError(response, 400, "recaptchaToken", "CAPTCHA_FAILED");
    assertNothingStoredFor(backend, email, copies);
  }

  @Test
  void AC_001_16_tokenConfirmedByTheVerificationServiceIsAccepted() {
    String email = uniqueEmail();
    ObjectNode body = external(email);
    body.put("recaptchaToken", "good-token");

    Response response = register(backend, body);

    assertThat(response.status()).as(response.text()).isEqualTo(201);
    assertThat(REQUESTS).anyMatch(r -> r.contains("response=good-token"));
    assertThat(exportRowsFor(backend, email)).hasSize(1);
  }
}

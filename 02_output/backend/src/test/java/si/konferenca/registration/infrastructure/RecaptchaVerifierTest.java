package si.konferenca.registration.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import si.konferenca.registration.config.AppProperties;
import si.konferenca.registration.config.StartupChecksTest;

class RecaptchaVerifierTest {

  private HttpServer server;
  private final AtomicReference<String> lastBody = new AtomicReference<>();

  @AfterEach
  void stop() {
    if (server != null) {
      server.stop(0);
    }
  }

  private String serve(int status, String body) throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/verify",
        exchange -> {
          lastBody.set(
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
          try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
          }
        });
    server.start();
    return "http://127.0.0.1:" + server.getAddress().getPort() + "/verify";
  }

  private static RecaptchaVerifier verifier(boolean testMode, String url) {
    AppProperties p = StartupChecksTest.validProduction();
    return new RecaptchaVerifier(
        new AppProperties(
            p.environment(),
            p.conferenceName(),
            p.optionsFile(),
            p.jsonCopyDir(),
            p.mailFrom(),
            new AppProperties.Recaptcha(testMode, "site", "the-secret", url),
            p.organizer(),
            p.corsAllowedOrigin(),
            p.rateLimit(),
            p.maxRequestBytes(),
            p.retention()));
  }

  @Test
  @DisplayName("DoD-P05 a token Google accepts passes; secret, token and address are sent")
  void acceptedToken() throws Exception {
    String url = serve(200, "{\"success\":true}");
    assertThat(verifier(false, url).verify("tok en", "10.1.2.3")).isTrue();
    assertThat(lastBody.get())
        .contains("secret=the-secret")
        .contains("response=tok+en")
        .contains("remoteip=10.1.2.3");
  }

  @Test
  @DisplayName("DoD-P05 a token Google rejects fails")
  void rejectedToken() throws Exception {
    assertThat(
            verifier(false, serve(200, "{\"success\":false,\"error-codes\":[\"x\"]}"))
                .verify("t", null))
        .isFalse();
  }

  @Test
  @DisplayName("SR-01 fails closed on server errors and malformed answers")
  void failsClosed() throws Exception {
    assertThat(verifier(false, serve(500, "oops")).verify("t", "ip")).isFalse();
    stop();
    assertThat(verifier(false, serve(200, "not json")).verify("t", "ip")).isFalse();
    stop();
    assertThat(verifier(false, serve(200, "{\"success\":\"true\"}")).verify("t", "ip")).isFalse();
    stop();
    assertThat(verifier(false, serve(200, "")).verify("t", "ip")).isFalse();
    stop();
    server = null;
    assertThat(verifier(false, "http://127.0.0.1:1/verify").verify("t", "ip")).isFalse();
  }

  @Test
  void blankTokenFailsWithoutACall() throws Exception {
    String url = serve(200, "{\"success\":true}");
    assertThat(verifier(false, url).verify("", "ip")).isFalse();
    assertThat(verifier(false, url).verify(null, "ip")).isFalse();
    assertThat(lastBody.get()).isNull();
  }

  @Test
  @DisplayName("SR-02 test mode accepts only the fixed token and makes no call")
  void testMode() throws Exception {
    String url = serve(200, "{\"success\":true}");
    RecaptchaVerifier v = verifier(true, url);
    assertThat(v.verify(RecaptchaVerifier.TEST_MODE_TOKEN, "ip")).isTrue();
    assertThat(v.verify("anything-else", "ip")).isFalse();
    assertThat(lastBody.get()).isNull();
  }
}

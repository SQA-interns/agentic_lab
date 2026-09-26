package si.konferenca.registration.infrastructure.captcha;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import si.konferenca.registration.TestFixtures;
import si.konferenca.registration.application.CaptchaFailedException;
import si.konferenca.registration.application.CaptchaUnavailableException;
import si.konferenca.registration.config.AppProperties;

class RecaptchaVerifierTest {

  private HttpServer server;
  private final AtomicReference<String> lastRequestBody = new AtomicReference<>();

  @AfterEach
  void stopServer() {
    if (server != null) {
      server.stop(0);
    }
  }

  private static AppProperties props(boolean testMode, String secret, String url) {
    AppProperties base = TestFixtures.properties(TestFixtures.conference());
    return new AppProperties(
        base.conference(),
        new AppProperties.Recaptcha(testMode, secret, "site", url),
        base.mail(),
        base.backup(),
        base.organizer(),
        base.rateLimit(),
        base.request(),
        base.cors());
  }

  private String startServer(String responseJson) throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/siteverify",
        exchange -> {
          lastRequestBody.set(
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          byte[] bytes = responseJson.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, bytes.length);
          try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
          }
        });
    server.start();
    return "http://127.0.0.1:" + server.getAddress().getPort() + "/siteverify";
  }

  @Test
  void testModeAcceptsOnlyTheDeterministicToken() {
    RecaptchaVerifier verifier = new RecaptchaVerifier(props(true, "", "http://unused"));
    assertThatCode(() -> verifier.verify(RecaptchaVerifier.TEST_MODE_TOKEN, "1.2.3.4"))
        .doesNotThrowAnyException();
    assertThatThrownBy(() -> verifier.verify("anything-else", "1.2.3.4"))
        .isInstanceOf(CaptchaFailedException.class);
  }

  @Test
  void blankTokenIsRejectedWithoutNetworkCall() {
    RecaptchaVerifier verifier = new RecaptchaVerifier(props(false, "secret", "http://unused"));
    for (String token : List.of("", "   ")) {
      assertThatThrownBy(() -> verifier.verify(token, "1.2.3.4"))
          .isInstanceOf(CaptchaFailedException.class);
    }
    assertThatThrownBy(() -> verifier.verify(null, "1.2.3.4"))
        .isInstanceOf(CaptchaFailedException.class);
  }

  @Test
  void productionModeRequiresSecret() {
    assertThatThrownBy(() -> new RecaptchaVerifier(props(false, "", "http://unused")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("RECAPTCHA_SECRET_KEY");
  }

  @Test
  void productionModeAcceptsSuccessfulVerification() throws IOException {
    String url = startServer("{\"success\":true,\"hostname\":\"example.org\"}");
    RecaptchaVerifier verifier = new RecaptchaVerifier(props(false, "s3cret", url));
    assertThatCode(() -> verifier.verify("user-token", "1.2.3.4")).doesNotThrowAnyException();
    assertThat(lastRequestBody.get())
        .contains("secret=s3cret")
        .contains("response=user-token")
        .contains("remoteip=1.2.3.4");
  }

  @Test
  void productionModeRejectsFailedVerification() throws IOException {
    String url = startServer("{\"success\":false,\"error-codes\":[\"invalid-input-response\"]}");
    RecaptchaVerifier verifier = new RecaptchaVerifier(props(false, "s3cret", url));
    assertThatThrownBy(() -> verifier.verify("user-token", "1.2.3.4"))
        .isInstanceOf(CaptchaFailedException.class);
  }

  @Test
  void productionModeReportsUnreachableService() {
    RecaptchaVerifier verifier =
        new RecaptchaVerifier(props(false, "s3cret", "http://127.0.0.1:1/siteverify"));
    assertThatThrownBy(() -> verifier.verify("user-token", "1.2.3.4"))
        .isInstanceOf(CaptchaUnavailableException.class);
  }
}

package si.konferenca.registration.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import si.konferenca.registration.application.CaptchaVerifier;

/**
 * The production reCAPTCHA code path against a mocked verification endpoint, including a rejected
 * token (DoD-P05, SR-01).
 */
class RecaptchaCaptchaVerifierTest {

  private HttpServer server;
  private final List<String> requests = new ArrayList<>();
  private volatile int status = 200;
  private volatile String body = "{\"success\": true}";
  private volatile long delayMillis;

  @BeforeEach
  void startStub() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/siteverify",
        exchange -> {
          requests.add(
              exchange.getRequestMethod()
                  + " "
                  + exchange.getRequestHeaders().getFirst("Content-Type")
                  + " "
                  + URLDecoder.decode(
                      new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8),
                      StandardCharsets.UTF_8));
          try {
            Thread.sleep(delayMillis);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          byte[] reply = body.getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(status, reply.length);
          exchange.getResponseBody().write(reply);
          exchange.close();
        });
    server.start();
  }

  @AfterEach
  void stopStub() {
    server.stop(0);
  }

  private RecaptchaCaptchaVerifier verifier() {
    return new RecaptchaCaptchaVerifier(
        URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/siteverify"),
        "secret-key");
  }

  @Test
  void acceptedTokenPassesAndSendsSecretTokenAndClientAddress() {
    assertThatCode(() -> verifier().verify("token-1", "203.0.113.5")).doesNotThrowAnyException();

    assertThat(requests)
        .containsExactly(
            "POST application/x-www-form-urlencoded"
                + " secret=secret-key&response=token-1&remoteip=203.0.113.5");
  }

  @Test
  void rejectedTokenFails() {
    body = "{\"success\": false, \"error-codes\": [\"invalid-input-response\"]}";

    assertThatThrownBy(() -> verifier().verify("forged", null))
        .isInstanceOf(CaptchaVerifier.CaptchaFailedException.class);
    assertThat(requests.get(0)).doesNotContain("remoteip");
  }

  @Test
  void answerWithoutSuccessFails() {
    body = "{}";

    assertThatThrownBy(() -> verifier().verify("t", "ip"))
        .isInstanceOf(CaptchaVerifier.CaptchaFailedException.class);
  }

  @Test
  void blankTokenFailsWithoutCallingGoogle() {
    assertThatThrownBy(() -> verifier().verify("  ", "ip"))
        .isInstanceOf(CaptchaVerifier.CaptchaFailedException.class);
    assertThatThrownBy(() -> verifier().verify(null, "ip"))
        .isInstanceOf(CaptchaVerifier.CaptchaFailedException.class);
    assertThat(requests).isEmpty();
  }

  @Test
  void errorStatusMeansUnavailable() {
    status = 500;

    assertThatThrownBy(() -> verifier().verify("t", "ip"))
        .isInstanceOf(CaptchaVerifier.CaptchaUnavailableException.class);
  }

  @Test
  void malformedAnswerMeansUnavailable() {
    body = "not json";

    assertThatThrownBy(() -> verifier().verify("t", "ip"))
        .isInstanceOf(CaptchaVerifier.CaptchaUnavailableException.class);
  }

  @Test
  void unreachableEndpointMeansUnavailable() {
    server.stop(0);

    assertThatThrownBy(() -> verifier().verify("t", "ip"))
        .isInstanceOf(CaptchaVerifier.CaptchaUnavailableException.class);
  }

  @Test
  void slowAnswerTimesOutAsUnavailable() {
    delayMillis = 6_000;

    assertThatThrownBy(() -> verifier().verify("t", "ip"))
        .isInstanceOf(CaptchaVerifier.CaptchaUnavailableException.class);
  }

  @Test
  void testModeAcceptsOnlyTheTestToken() {
    TestModeCaptchaVerifier testMode = new TestModeCaptchaVerifier();

    assertThatCode(() -> testMode.verify("test-valid", null)).doesNotThrowAnyException();
    assertThatThrownBy(() -> testMode.verify("Test-Valid", null))
        .isInstanceOf(CaptchaVerifier.CaptchaFailedException.class);
    assertThatThrownBy(() -> testMode.verify(null, null))
        .isInstanceOf(CaptchaVerifier.CaptchaFailedException.class);
  }
}

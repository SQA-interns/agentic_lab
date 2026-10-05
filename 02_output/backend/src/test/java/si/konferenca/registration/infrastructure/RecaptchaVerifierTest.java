package si.konferenca.registration.infrastructure;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * DoD-P05: the production verification path against a mocked endpoint that implements
 * recaptcha-siteverify.openapi.yaml, including a rejected token.
 */
class RecaptchaVerifierTest {

  private final AtomicInteger status = new AtomicInteger(200);
  private final AtomicReference<String> answer = new AtomicReference<>("{\"success\":true}");
  private final AtomicReference<String> received = new AtomicReference<>();
  private HttpServer server;
  private RecaptchaVerifier verifier;

  @BeforeEach
  void start() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/recaptcha/api/siteverify",
        exchange -> {
          received.set(
              exchange.getRequestMethod()
                  + " "
                  + exchange.getRequestHeaders().getFirst("Content-Type")
                  + " "
                  + URLDecoder.decode(
                      new String(exchange.getRequestBody().readAllBytes(), UTF_8), UTF_8));
          byte[] body = answer.get().getBytes(UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(status.get(), body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    server.start();
    String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/recaptcha/api/siteverify";
    verifier = new RecaptchaVerifier(url, "secret-key-value", JsonMapper.builder().build());
  }

  @AfterEach
  void stop() {
    server.stop(0);
  }

  @Test
  void acceptedTokenPassesAndSendsSecretTokenAndAddress() {
    assertThat(verifier.verify("token-1", "10.1.2.3")).isTrue();
    assertThat(received.get())
        .startsWith("POST application/x-www-form-urlencoded")
        .contains("secret=secret-key-value")
        .contains("response=token-1")
        .contains("remoteip=10.1.2.3");
  }

  @Test
  void rejectedTokenFails() {
    answer.set("{\"success\":false,\"error-codes\":[\"invalid-input-response\"]}");
    assertThat(verifier.verify("bad", null)).isFalse();
    assertThat(received.get()).doesNotContain("remoteip");
  }

  @Test
  void serverErrorsAndUnreadableAnswersFail() {
    status.set(500);
    assertThat(verifier.verify("t", null)).isFalse();
    status.set(200);
    answer.set("not json");
    assertThat(verifier.verify("t", null)).isFalse();
    answer.set("{\"success\":\"true\"}");
    assertThat(verifier.verify("t", null)).isFalse();
    answer.set("{}");
    assertThat(verifier.verify("t", null)).isFalse();
  }

  @Test
  void unreachableEndpointFails() {
    server.stop(0);
    assertThat(verifier.verify("t", null)).isFalse();
  }

  @Test
  void testModeAcceptsOnlyThePassingToken() {
    TestModeCaptchaVerifier testMode = new TestModeCaptchaVerifier();
    assertThat(testMode.verify("test-pass", null)).isTrue();
    assertThat(testMode.verify("test-fail", null)).isFalse();
    assertThat(testMode.verify(null, null)).isFalse();
  }
}

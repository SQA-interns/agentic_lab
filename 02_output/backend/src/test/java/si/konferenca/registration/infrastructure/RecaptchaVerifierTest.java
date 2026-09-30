package si.konferenca.registration.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RecaptchaVerifierTest {

  private HttpServer server;
  private final List<String> bodies = new ArrayList<>();
  private volatile int status = 200;
  private volatile String answer = "{\"success\":true}";

  @BeforeEach
  void start() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/verify",
        exchange -> {
          bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          byte[] body = answer.getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(status, body.length);
          try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
          }
        });
    server.start();
  }

  @AfterEach
  void stop() {
    server.stop(0);
  }

  private RecaptchaVerifier verifier() {
    return new RecaptchaVerifier(
        URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/verify"), "s&cret");
  }

  @Test
  void acceptsOnlyAConfirmedToken() {
    assertThat(verifier().verify("tok en")).isTrue();
    assertThat(bodies).containsExactly("secret=s%26cret&response=tok+en");
    assertThat(verifier().testMode()).isFalse();

    answer = "{\"success\":false}";
    assertThat(verifier().verify("x")).isFalse();
    answer = "{}";
    assertThat(verifier().verify("x")).isFalse();
    answer = "not json";
    assertThat(verifier().verify("x")).isFalse();
    answer = "{\"success\":true}";
    status = 500;
    assertThat(verifier().verify("x")).isFalse();
  }

  @Test
  void blankTokenIsRejectedWithoutACall() {
    assertThat(verifier().verify(" ")).isFalse();
    assertThat(verifier().verify(null)).isFalse();
    assertThat(bodies).isEmpty();
  }

  @Test
  void unreachableServiceIsARejection() {
    RecaptchaVerifier unreachable =
        new RecaptchaVerifier(URI.create("http://127.0.0.1:1/verify"), "s");

    assertThat(unreachable.verify("x")).isFalse();
  }

  @Test
  void testModeAcceptsOnlyTheFixedToken() {
    TestModeCaptchaVerifier testMode = new TestModeCaptchaVerifier();

    assertThat(testMode.verify("test-mode-token")).isTrue();
    assertThat(testMode.verify("other")).isFalse();
    assertThat(testMode.testMode()).isTrue();
  }
}

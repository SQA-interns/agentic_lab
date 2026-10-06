package si.konferenca.registration.infrastructure.captcha;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import si.konferenca.registration.application.RegistrationPorts.CaptchaResult;

class CaptchaVerifierTest {

  private HttpServer server;
  private final AtomicReference<String> body = new AtomicReference<>();
  private volatile int status = 200;
  private volatile String answer = "{\"success\":true}";

  @BeforeEach
  void start() throws IOException {
    server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    server.createContext(
        "/verify",
        exchange -> {
          body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          byte[] bytes = answer.getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(status, bytes.length);
          exchange.getResponseBody().write(bytes);
          exchange.close();
        });
    server.start();
  }

  @AfterEach
  void stop() {
    server.stop(0);
  }

  private RecaptchaVerifier verifier() {
    return new RecaptchaVerifier(
        "http://localhost:" + server.getAddress().getPort() + "/verify", "s3cret&key");
  }

  @Test
  void successIsPassedAndTheFormIsEncoded() {
    assertThat(verifier().verify("tok en+/=")).isEqualTo(CaptchaResult.PASSED);
    assertThat(body.get()).isEqualTo("secret=s3cret%26key&response=tok+en%2B%2F%3D");
  }

  @Test
  void falseSuccessFails() {
    answer = "{\"success\":false,\"error-codes\":[\"timeout-or-duplicate\"]}";
    assertThat(verifier().verify("t")).isEqualTo(CaptchaResult.FAILED);
  }

  @Test
  void unexpectedAnswersCountAsUnavailable() {
    answer = "{\"success\":\"true\"}";
    assertThat(verifier().verify("t")).isEqualTo(CaptchaResult.UNAVAILABLE);
    answer = "{}";
    assertThat(verifier().verify("t")).isEqualTo(CaptchaResult.UNAVAILABLE);
    answer = "<html>";
    assertThat(verifier().verify("t")).isEqualTo(CaptchaResult.UNAVAILABLE);
    answer = "{\"success\":true}";
    status = 502;
    assertThat(verifier().verify("t")).isEqualTo(CaptchaResult.UNAVAILABLE);
  }

  @Test
  void unreachableServiceCountsAsUnavailable() throws IOException {
    int port;
    try (ServerSocket socket = new ServerSocket(0)) {
      port = socket.getLocalPort();
    }
    RecaptchaVerifier closed = new RecaptchaVerifier("http://localhost:" + port + "/verify", "s");
    assertThat(closed.verify("t")).isEqualTo(CaptchaResult.UNAVAILABLE);
  }

  @Test
  void testModeAcceptsOnlyItsToken() {
    TestModeCaptchaVerifier testMode = new TestModeCaptchaVerifier();
    assertThat(testMode.verify("test-pass")).isEqualTo(CaptchaResult.PASSED);
    assertThat(testMode.verify("test-pass ")).isEqualTo(CaptchaResult.FAILED);
    assertThat(testMode.verify(null)).isEqualTo(CaptchaResult.FAILED);
  }
}

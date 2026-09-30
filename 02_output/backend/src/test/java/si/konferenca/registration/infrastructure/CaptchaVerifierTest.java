package si.konferenca.registration.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CaptchaVerifierTest {

  private HttpServer server;
  private final List<String> bodies = new CopyOnWriteArrayList<>();
  private volatile int status = 200;
  private volatile String answer = "{\"success\":true}";

  @BeforeEach
  void start() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/verify",
        ex -> {
          bodies.add(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          byte[] out = answer.getBytes(StandardCharsets.UTF_8);
          ex.sendResponseHeaders(status, out.length);
          try (OutputStream os = ex.getResponseBody()) {
            os.write(out);
          }
        });
    server.start();
  }

  @AfterEach
  void stop() {
    server.stop(0);
  }

  private GoogleCaptchaVerifier verifier() {
    return new GoogleCaptchaVerifier(
        "http://127.0.0.1:" + server.getAddress().getPort() + "/verify", "s3cret&=");
  }

  @Test
  void postsUrlEncodedSecretTokenAndAddress() {
    assertThat(verifier().verify("tok en", "10.0.0.9")).isTrue();

    assertThat(bodies).containsExactly("secret=s3cret%26%3D&response=tok+en&remoteip=10.0.0.9");
  }

  @Test
  void omitsMissingAddressAndEncodesMissingToken() {
    assertThat(verifier().verify(null, null)).isTrue();

    assertThat(bodies).containsExactly("secret=s3cret%26%3D&response=");
  }

  @Test
  void rejectsUnsuccessfulAnswer() {
    answer = "{\"success\":false,\"error-codes\":[\"invalid-input-response\"]}";

    assertThat(verifier().verify("t", "1.2.3.4")).isFalse();
  }

  @Test
  void rejectsNonBooleanSuccess() {
    answer = "{\"success\":\"true\"}";

    assertThat(verifier().verify("t", "1.2.3.4")).isFalse();
  }

  @Test
  void rejectsHttpErrorAndInvalidJson() {
    status = 500;
    assertThat(verifier().verify("t", "1.2.3.4")).isFalse();
    status = 200;
    answer = "not json";
    assertThat(verifier().verify("t", "1.2.3.4")).isFalse();
  }

  @Test
  void rejectsWhenUnreachable() {
    GoogleCaptchaVerifier unreachable = new GoogleCaptchaVerifier("http://127.0.0.1:1/x", "s");

    assertThat(unreachable.verify("t", "1.2.3.4")).isFalse();
  }

  @Test
  void testModeAcceptsOnlyThePassToken() {
    TestModeCaptchaVerifier testMode = new TestModeCaptchaVerifier();

    assertThat(testMode.verify("test-mode-pass", null)).isTrue();
    assertThat(testMode.verify("test-mode-pass ", null)).isFalse();
    assertThat(testMode.verify(null, null)).isFalse();
  }
}

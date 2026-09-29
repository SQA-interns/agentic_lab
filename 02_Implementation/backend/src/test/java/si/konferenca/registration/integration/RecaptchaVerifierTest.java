package si.konferenca.registration.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** Production-mode verification against a local stand-in for Google's siteverify endpoint. */
class RecaptchaVerifierTest {

  private HttpServer server;
  private final AtomicReference<String> lastRequestBody = new AtomicReference<>();

  private URI serve(int status, String body) throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/siteverify",
        exchange -> {
          lastRequestBody.set(
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(status, bytes.length);
          try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
          }
        });
    server.start();
    return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/siteverify");
  }

  @AfterEach
  void stop() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  void testModeAcceptsOnlyTheTestToken() {
    RecaptchaVerifier verifier = new RecaptchaVerifier(true, "", URI.create("http://unused"));

    assertThat(verifier.verify("test-pass", "1.2.3.4")).isTrue();
    assertThat(verifier.verify("test-fail", "1.2.3.4")).isFalse();
    assertThat(verifier.verify("anything", null)).isFalse();
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  void blankTokensFailWithoutCallingTheProvider(String token) throws IOException {
    URI url = serve(200, "{\"success\":true}");
    RecaptchaVerifier verifier = new RecaptchaVerifier(false, "secret", url);

    assertThat(verifier.verify(token, "1.2.3.4")).isFalse();
    assertThat(lastRequestBody.get()).isNull();
  }

  @Test
  void oversizedTokenIsRejected() throws IOException {
    RecaptchaVerifier verifier =
        new RecaptchaVerifier(false, "secret", serve(200, "{\"success\":true}"));

    assertThat(verifier.verify("x".repeat(4097), null)).isFalse();
  }

  @Test
  void productionModeSendsSecretTokenAndIpAndAcceptsSuccess() throws IOException {
    RecaptchaVerifier verifier =
        new RecaptchaVerifier(false, "s3cr&t", serve(200, "{\"success\":true}"));

    assertThat(verifier.verify("tok en", "10.0.0.1")).isTrue();
    assertThat(lastRequestBody.get())
        .isEqualTo("secret=s3cr%26t&response=tok+en&remoteip=10.0.0.1");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{\"success\":false,\"error-codes\":[\"invalid-input-response\"]}",
        "{\"success\":\"true\"}",
        "{}",
        "not json"
      })
  void productionModeRejectsAnythingButSuccessTrue(String reply) throws IOException {
    RecaptchaVerifier verifier = new RecaptchaVerifier(false, "secret", serve(200, reply));

    assertThat(verifier.verify("token", null)).isFalse();
  }

  @Test
  void productionModeFailsClosedOnHttpError() throws IOException {
    RecaptchaVerifier verifier =
        new RecaptchaVerifier(false, "secret", serve(500, "{\"success\":true}"));

    assertThat(verifier.verify("token", null)).isFalse();
  }

  @Test
  void productionModeFailsClosedWhenProviderUnreachable() throws IOException {
    URI url = serve(200, "{\"success\":true}");
    server.stop(0);
    server = null;

    assertThat(new RecaptchaVerifier(false, "secret", url).verify("token", null)).isFalse();
  }
}

package lab.conference.registration;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Server verification against a local fake of the siteverify endpoint (no Google calls). */
class RecaptchaVerifierTest {

  private HttpServer server;
  private final AtomicReference<String> lastForm = new AtomicReference<>();

  private RecaptchaVerifier verifierAnswering(int status, String body) throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/siteverify",
        exchange -> {
          lastForm.set(
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(status, bytes.length);
          exchange.getResponseBody().write(bytes);
          exchange.close();
        });
    server.start();
    URI uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/siteverify");
    return new RecaptchaVerifier("site", "s3cret", HttpClient.newHttpClient(), uri);
  }

  @AfterEach
  void stop() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  void successIsAccepted() throws Exception {
    assertThat(verifierAnswering(200, "{\"success\":true}").verify("tok&x", "1.2.3.4")).isTrue();
    assertThat(lastForm.get()).contains("secret=s3cret", "response=tok%26x", "remoteip=1.2.3.4");
  }

  @Test
  void failureAnswersAreRejected() throws Exception {
    assertThat(verifierAnswering(200, "{\"success\":false}").verify("t", "a")).isFalse();
  }

  @Test
  void httpErrorsAndGarbageFailClosed() throws Exception {
    assertThat(verifierAnswering(500, "{\"success\":true}").verify("t", "a")).isFalse();
    stop();
    assertThat(verifierAnswering(200, "not json").verify("t", null)).isFalse();
  }

  @Test
  void unreachableServiceFailsClosed() {
    RecaptchaVerifier v =
        new RecaptchaVerifier(
            "s", "k", HttpClient.newHttpClient(), URI.create("http://127.0.0.1:9/x"));
    assertThat(v.verify("t", "a")).isFalse();
    assertThat(v.mode()).isEqualTo("recaptcha");
  }
}

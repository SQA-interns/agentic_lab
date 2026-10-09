package si.konferenca.registration.adapter.captcha;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class CaptchaVerifierTest {

  private HttpServer server;
  private final AtomicReference<String> received = new AtomicReference<>();

  private String serve(int status, String body) throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/verify",
        exchange -> {
          received.set(
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(status, bytes.length);
          try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
          }
        });
    server.start();
    return "http://127.0.0.1:" + server.getAddress().getPort() + "/verify";
  }

  @AfterEach
  void stop() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  void sr01_successFromGoogleIsAccepted() throws IOException {
    GoogleCaptchaVerifier verifier =
        new GoogleCaptchaVerifier(serve(200, "{\"success\":true}"), "s3");

    assertThat(verifier.verify("tok", "10.0.0.1")).isTrue();
    assertThat(received.get()).contains("secret=s3", "response=tok", "remoteip=10.0.0.1");
  }

  @Test
  void dodP05_rejectedTokenIsRefused() throws IOException {
    GoogleCaptchaVerifier verifier =
        new GoogleCaptchaVerifier(
            serve(200, "{\"success\":false,\"error-codes\":[\"invalid-input-response\"]}"), "s");

    assertThat(verifier.verify("tok", null)).isFalse();
  }

  @Test
  void serverErrorOrUnreachableServiceIsRefused() throws IOException {
    assertThat(new GoogleCaptchaVerifier(serve(500, "oops"), "s").verify("tok", null)).isFalse();
    int closed;
    try (ServerSocket socket = new ServerSocket(0)) {
      closed = socket.getLocalPort();
    }
    assertThat(
            new GoogleCaptchaVerifier("http://127.0.0.1:" + closed + "/v", "s").verify("t", null))
        .isFalse();
  }

  @Test
  void sr02_testModeAcceptsOnlyItsToken() {
    TestModeCaptchaVerifier verifier = new TestModeCaptchaVerifier();

    assertThat(verifier.verify("test-pass", null)).isTrue();
    assertThat(verifier.verify("test-pass ", null)).isFalse();
    assertThat(verifier.verify(null, null)).isFalse();
  }
}

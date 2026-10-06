package si.konferenca.registration.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import si.konferenca.registration.config.AppProperties;
import si.konferenca.registration.integration.AntiAutomationVerifier.Outcome;

class AntiAutomationVerifierTest {

  private HttpServer server;

  private String serve(int status, String body) throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/siteverify",
        exchange -> {
          byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(status, bytes.length);
          exchange.getResponseBody().write(bytes);
          exchange.close();
        });
    server.start();
    return "http://127.0.0.1:" + server.getAddress().getPort() + "/siteverify";
  }

  @AfterEach
  void stop() {
    if (server != null) {
      server.stop(0);
    }
  }

  private static AntiAutomationVerifier verifier(boolean testMode, String url) {
    return new AntiAutomationVerifier(
        new AppProperties(
            "test",
            "C",
            "",
            "/tmp",
            null,
            new AppProperties.Recaptcha(testMode, "site", "secret", url),
            null,
            "",
            null,
            1));
  }

  @Test
  void testModeAcceptsOnlyTheTestToken() {
    AntiAutomationVerifier verifier = verifier(true, "http://127.0.0.1:1/never-called");

    assertThat(verifier.verify("test-mode-pass", "1.2.3.4")).isEqualTo(Outcome.PASSED);
    assertThat(verifier.verify("other", "1.2.3.4")).isEqualTo(Outcome.FAILED);
    assertThat(verifier.verify(" ", null)).isEqualTo(Outcome.FAILED);
    assertThat(verifier.verify("x".repeat(4097), null)).isEqualTo(Outcome.FAILED);
  }

  @Test
  void liveModeFailsWhenSuccessIsMissingOrFalse() throws IOException {
    assertThat(verifier(false, serve(200, "{}")).verify("t", null)).isEqualTo(Outcome.FAILED);
    stop();
    assertThat(verifier(false, serve(200, "{\"success\":false}")).verify("t", "1.2.3.4"))
        .isEqualTo(Outcome.FAILED);
  }

  @Test
  void liveModeIsUnavailableOnABrokenOrUnreachableService() throws IOException {
    assertThat(verifier(false, serve(200, "not json")).verify("t", null))
        .isEqualTo(Outcome.UNAVAILABLE);
    stop();
    assertThat(verifier(false, serve(302, "")).verify("t", null)).isEqualTo(Outcome.UNAVAILABLE);
    stop();
    server = null;
    assertThat(verifier(false, "http://127.0.0.1:1/siteverify").verify("t", null))
        .isEqualTo(Outcome.UNAVAILABLE);
  }
}

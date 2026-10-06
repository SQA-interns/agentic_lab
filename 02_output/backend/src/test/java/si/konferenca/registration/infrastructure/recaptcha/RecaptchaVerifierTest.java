package si.konferenca.registration.infrastructure.recaptcha;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import si.konferenca.registration.application.ServiceUnavailableException;

/** Production verification path against a local stub (DoD-P05); no call reaches Google. */
class RecaptchaVerifierTest {

  private HttpServer server;
  private final AtomicReference<String> lastForm = new AtomicReference<>();

  private String stub(int status, String body) throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/verify",
        ex -> {
          lastForm.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          byte[] b = body.getBytes(StandardCharsets.UTF_8);
          ex.sendResponseHeaders(status, b.length);
          try (OutputStream o = ex.getResponseBody()) {
            o.write(b);
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
  void testModeAcceptsOnlyThePassToken() {
    RecaptchaVerifier v = new RecaptchaVerifier(true, "", "", "http://127.0.0.1:1/never");

    assertThat(v.verify("test-pass", null)).isTrue();
    assertThat(v.verify("test-fail", null)).isFalse();
    assertThat(v.verify(" ", null)).isFalse();
    assertThat(v.verify(null, null)).isFalse();
    assertThat(v.testMode()).isTrue();
  }

  @Test
  void successfulVerificationSendsSecretTokenAndAddress() throws Exception {
    RecaptchaVerifier v =
        new RecaptchaVerifier(false, "site", "s&ecret", stub(200, "{\"success\":true}"));

    assertThat(v.verify("tok en", "10.1.2.3")).isTrue();
    assertThat(lastForm.get()).isEqualTo("secret=s%26ecret&response=tok+en&remoteip=10.1.2.3");
    assertThat(v.siteKey()).isEqualTo("site");
  }

  @Test
  void rejectedTokenIsFalseAndAddressIsOptional() throws Exception {
    RecaptchaVerifier v =
        new RecaptchaVerifier(
            false, null, null, stub(200, "{\"success\":false,\"error-codes\":[\"x\"]}"));

    assertThat(v.verify("tok", null)).isFalse();
    assertThat(lastForm.get()).isEqualTo("secret=&response=tok");
    assertThat(v.siteKey()).isEmpty();
  }

  @Test
  void missingSuccessFieldIsFalse() throws Exception {
    assertThat(new RecaptchaVerifier(false, "s", "k", stub(200, "{}")).verify("t", null)).isFalse();
  }

  @Test
  void serverErrorIsUnavailable() throws Exception {
    RecaptchaVerifier v = new RecaptchaVerifier(false, "s", "k", stub(500, "oops"));

    assertThatThrownBy(() -> v.verify("t", null)).isInstanceOf(ServiceUnavailableException.class);
  }

  @Test
  void invalidJsonIsUnavailable() throws Exception {
    RecaptchaVerifier v = new RecaptchaVerifier(false, "s", "k", stub(200, "not json"));

    assertThatThrownBy(() -> v.verify("t", null)).isInstanceOf(ServiceUnavailableException.class);
  }

  @Test
  void unreachableServiceIsUnavailable() {
    RecaptchaVerifier v = new RecaptchaVerifier(false, "s", "k", "http://127.0.0.1:1/verify");

    assertThatThrownBy(() -> v.verify("t", null)).isInstanceOf(ServiceUnavailableException.class);
  }
}

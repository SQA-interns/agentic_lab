package si.konferenca.registration.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import si.konferenca.registration.acceptance.support.AcceptanceTest;
import si.konferenca.registration.acceptance.support.TestStack;

/** F-02 through the running application: an encoded export path keeps the HTTPS-only rule. */
class EncodedPathIntegrationTest extends AcceptanceTest {

  private final HttpClient http = HttpClient.newHttpClient();

  @LocalServerPort private int port;

  @Test
  void encodedExportPathOverPlainHttpFromOutsideIsRefused() throws Exception {
    String basic =
        Base64.getEncoder()
            .encodeToString(
                (TestStack.ORGANIZER_USERNAME + ":" + TestStack.ORGANIZER_PASSWORD)
                    .getBytes(StandardCharsets.UTF_8));

    HttpResponse<String> response =
        http.send(
            HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/%65xport"))
                .header("Authorization", "Basic " + basic)
                .header("X-Forwarded-For", "203.0.113.5")
                .build(),
            HttpResponse.BodyHandlers.ofString());

    assertThat(response.statusCode()).isEqualTo(403);
    assertThat(response.body()).contains("https_required");
  }
}

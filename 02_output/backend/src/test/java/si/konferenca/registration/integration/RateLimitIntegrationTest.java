package si.konferenca.registration.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static si.konferenca.registration.integration.HttpSupport.basic;
import static si.konferenca.registration.integration.HttpSupport.request;
import static si.konferenca.registration.integration.HttpSupport.send;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import si.konferenca.registration.acceptance.support.AcceptanceTestBase;

/** SR-03, SB-06: registrations and export attempts are limited per client. */
class RateLimitIntegrationTest extends AcceptanceTestBase {

  @Value("${local.server.port}")
  int port;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    configure(
        registry,
        Map.of("RATE_LIMIT_REGISTRATIONS_PER_MINUTE", "2", "RATE_LIMIT_EXPORTS_PER_MINUTE", "2"));
  }

  @Test
  void registrationAttemptsOverTheLimitGet429() {
    int[] statuses = new int[3];
    for (int i = 0; i < 3; i++) {
      HttpResponse<String> response =
          send(
              request(port, "/api/registrations")
                  .header("Content-Type", "application/json")
                  .POST(HttpRequest.BodyPublishers.ofString("{}")));
      statuses[i] = response.statusCode();
      if (i == 2) {
        assertThat(response.headers().firstValue("Retry-After")).isPresent();
      }
    }

    assertThat(statuses).containsExactly(400, 400, 429);
  }

  @Test
  void passwordGuessingOnTheExportIsLimited() {
    int last = 0;
    for (int i = 0; i < 3; i++) {
      last =
          send(request(port, "/api/registrations/export")
                  .header("Authorization", basic("organizer", "guess-" + i))
                  .GET())
              .statusCode();
    }

    assertThat(last).isEqualTo(429);
  }
}

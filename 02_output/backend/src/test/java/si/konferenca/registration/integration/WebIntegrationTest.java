package si.konferenca.registration.integration;

import static org.assertj.core.api.Assertions.assertThat;
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
import si.konferenca.registration.acceptance.support.Database;
import si.konferenca.registration.acceptance.support.TestEnvironment;

/** Error paths, headers and health of the running backend (SB-07, SB-10, SR-03, NFR-04). */
class WebIntegrationTest extends AcceptanceTestBase {

  @Value("${local.server.port}")
  int port;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    configure(registry, Map.of("MAX_REQUEST_BYTES", "2048"));
  }

  private HttpResponse<String> postJson(String body) {
    return send(
        request(port, "/api/registrations")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body)));
  }

  @Test
  void apiResponsesCarryTheSecurityHeaders() {
    HttpResponse<String> response = send(request(port, "/api/form-config").GET());

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.headers().firstValue("Content-Security-Policy"))
        .contains("default-src 'none'; frame-ancestors 'none'");
    assertThat(response.headers().firstValue("X-Frame-Options")).contains("DENY");
    assertThat(response.headers().firstValue("X-Content-Type-Options")).contains("nosniff");
    assertThat(response.headers().firstValue("Referrer-Policy")).contains("no-referrer");
    assertThat(response.headers().firstValue("Set-Cookie")).isEmpty();
  }

  @Test
  void malformedJsonIsABadRequestWithoutDetails() {
    HttpResponse<String> response = postJson("{\"type\": ");

    assertThat(response.statusCode()).isEqualTo(400);
    assertThat(response.headers().firstValue("Content-Type")).contains("application/problem+json");
    assertThat(response.body()).contains("\"field\":\"body\"").contains("MALFORMED");
    assertThat(response.body().toLowerCase(java.util.Locale.ROOT))
        .doesNotContain("jackson")
        .doesNotContain("exception");
  }

  @Test
  void unknownPropertyIsMalformed() {
    HttpResponse<String> response = postJson("{\"type\":\"external\",\"phone\":\"123\"}");

    assertThat(response.statusCode()).isEqualTo(400);
    assertThat(response.body()).contains("MALFORMED");
  }

  @Test
  void otherContentTypesAreUnsupported() {
    HttpResponse<String> response =
        send(
            request(port, "/api/registrations")
                .header("Content-Type", "text/plain")
                .POST(HttpRequest.BodyPublishers.ofString("hello")));

    assertThat(response.statusCode()).isEqualTo(415);
    assertThat(response.body()).contains("\"status\":415");
  }

  @Test
  void bodyOverTheSizeLimitIsRefused() {
    HttpResponse<String> response = postJson("{\"firstName\":\"" + "a".repeat(3000) + "\"}");

    assertThat(response.statusCode()).isEqualTo(413);
    assertThat(response.body()).contains("\"status\":413");
    assertThat(Database.registrationCount()).isZero();
  }

  @Test
  void unknownPathsAndMethodsAreRefusedWithoutDetails() {
    HttpResponse<String> unknown = send(request(port, "/api/registrations/123").GET());
    HttpResponse<String> listing = send(request(port, "/api/registrations").GET());
    HttpResponse<String> actuator = send(request(port, "/actuator/env").GET());

    assertThat(unknown.statusCode()).isEqualTo(401);
    assertThat(listing.statusCode()).isEqualTo(401);
    assertThat(actuator.statusCode()).isEqualTo(401);
    assertThat(listing.headers().firstValue("WWW-Authenticate"))
        .contains("Basic realm=\"organizer\"");
  }

  @Test
  void organizerCanReachTheExportOverPlainHttpWhenHttpsOnlyIsOff() {
    HttpResponse<String> response =
        send(
            request(port, "/api/registrations/export")
                .header(
                    "Authorization",
                    HttpSupport.basic(
                        TestEnvironment.ORGANIZER_USERNAME, TestEnvironment.ORGANIZER_PASSWORD))
                .GET());

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.headers().firstValue("Set-Cookie")).isEmpty();
  }

  @Test
  void livenessAndReadinessArePublicAndUp() {
    HttpResponse<String> liveness = send(request(port, "/actuator/health/liveness").GET());
    HttpResponse<String> readiness = send(request(port, "/actuator/health/readiness").GET());

    assertThat(liveness.statusCode()).isEqualTo(200);
    assertThat(liveness.body()).contains("\"status\":\"UP\"");
    assertThat(readiness.statusCode()).isEqualTo(200);
    assertThat(readiness.body()).contains("\"status\":\"UP\"");
  }
}

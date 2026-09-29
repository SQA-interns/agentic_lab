package si.konferenca.registration.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import si.konferenca.registration.acceptance.support.AcceptanceTestBase;
import si.konferenca.registration.acceptance.support.Api;

/** NFR-04, ES-09 health and readiness; SB-10 security headers on API responses. */
class OperationsAcceptanceTest extends AcceptanceTestBase {

  @Test
  @DisplayName("NFR-04 the backend reports liveness and readiness")
  void nfr04HealthAndReadiness() {
    Api.Response liveness = api.get("/actuator/health/liveness");
    Api.Response readiness = api.get("/actuator/health/readiness");

    assertThat(liveness.status()).as(liveness.text()).isEqualTo(200);
    assertThat(liveness.json().path("status").asString()).isEqualTo("UP");
    assertThat(readiness.status()).as(readiness.text()).isEqualTo(200);
    assertThat(readiness.json().path("status").asString()).isEqualTo("UP");
  }

  @Test
  @DisplayName("SB-10 API responses carry security headers")
  void sb10SecurityHeaders() {
    Api.Response r = api.get("/api/options?type=EXTERNAL");

    assertThat(r.status()).isEqualTo(200);
    assertThat(r.header("X-Content-Type-Options")).isEqualTo("nosniff");
    assertThat(r.header("X-Frame-Options")).isEqualTo("DENY");
    assertThat(r.header("Content-Security-Policy")).contains("frame-ancestors 'none'");
  }

  @Test
  @DisplayName("ES-07 SB-07 a malformed request gets a generic error without internals")
  void es07MalformedRequestHasNoInternals() {
    Api.Response r =
        api.postRaw(
            "/api/registrations", "{\"type\": ".getBytes(java.nio.charset.StandardCharsets.UTF_8));

    assertThat(r.status()).isEqualTo(400);
    assertThat(r.json().path("message").asString()).isNotBlank();
    assertNoInternals(r);
  }
}

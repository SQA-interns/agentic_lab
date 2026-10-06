package si.konferenca.registration.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import si.konferenca.registration.acceptance.support.AcceptanceEnvironment;
import si.konferenca.registration.acceptance.support.AcceptanceTestBase;
import si.konferenca.registration.acceptance.support.Api;
import si.konferenca.registration.acceptance.support.Payloads;

/** SR-03, SR-06, SB-10, ES-07 against a running backend with low limits and HTTPS-only on. */
class SecurityControlsIntegrationTest extends AcceptanceTestBase {

  @DynamicPropertySource
  static void strictSettings(DynamicPropertyRegistry registry) {
    registry.add("app.rate-limit.registrations", () -> "3");
    registry.add("app.rate-limit.exports", () -> "2");
    registry.add("app.max-request-bytes", () -> "2048");
    registry.add("app.organizer.https-only", () -> "true");
  }

  private static String basic() {
    return "Basic "
        + Base64.getEncoder()
            .encodeToString(
                (AcceptanceEnvironment.ORGANIZER_USERNAME
                        + ":"
                        + AcceptanceEnvironment.ORGANIZER_PASSWORD)
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  @Test
  void registrationsAboveTheLimitAreRefusedWithRetryAfter() {
    // Other tests of this class may already have used part of the shared one-minute window, and
    // the window may roll over once during the loop: 2 * 3 + 1 requests always reach the limit.
    int accepted = 0;
    Api.Response limited = null;
    for (int i = 0; i < 7 && limited == null; i++) {
      Api.Response response = api.register(Payloads.external());
      if (response.status() == 201) {
        accepted++;
      } else {
        limited = response;
      }
    }

    assertThat(limited).as("a request above the limit of 3").isNotNull();
    assertError(limited, 429, "RATE_LIMITED");
    assertThat(Integer.parseInt(limited.header("Retry-After"))).isBetween(1, 60);
    assertThat(accepted).isLessThanOrEqualTo(6);
    assertThat(db.countRegistrations()).isEqualTo(accepted);
  }

  @Test
  void bodiesAboveTheLimitAreRefusedBeforeParsing() {
    Map<String, Object> body = Payloads.with(Payloads.external(), "organization", "x".repeat(3000));

    assertError(api.register(body), 413, "PAYLOAD_TOO_LARGE");
    assertNothingStored();
  }

  @Test
  void nonJsonBodiesAreRefused() {
    assertError(
        api.postRaw("/api/registrations", "text/plain", "hello"), 415, "UNSUPPORTED_MEDIA_TYPE");
    assertError(
        api.postRaw("/api/registrations", "application/json", "{\"x\":1}"),
        400,
        "MALFORMED_REQUEST");
  }

  @Test
  void organizerAccessOverPlainHttpIsRefusedBeforeCredentialsAreChecked() {
    Api.Response response = api.get("/api/export/registrations.xlsx", "Authorization", basic());

    assertError(response, 403, "HTTPS_REQUIRED");
    assertThat(response.header("WWW-Authenticate")).isNull();
  }

  @Test
  void organizerAccessOverHttpsFromTheTrustedProxyIsAllowed() {
    Api.Response response =
        api.get(
            "/api/export/registrations.xlsx",
            "Authorization",
            basic(),
            "X-Forwarded-Proto",
            "https");

    assertThat(response.status()).as(response.toString()).isEqualTo(200);
  }

  @Test
  void apiResponsesCarrySecurityHeadersAndUnknownPathsAreNotFound() {
    Api.Response response = api.formConfig();

    assertThat(response.header("Content-Security-Policy")).contains("default-src 'none'");
    assertThat(response.header("X-Content-Type-Options")).isEqualTo("nosniff");
    assertThat(response.header("X-Frame-Options")).isEqualTo("DENY");
    assertThat(response.header("Referrer-Policy")).isEqualTo("no-referrer");
    assertThat(api.get("/api/internal").status()).isEqualTo(404);
    assertThat(api.get("/actuator/env").status()).isEqualTo(404);
    assertThat(api.get("/actuator/health/readiness").status()).isEqualTo(200);
  }
}

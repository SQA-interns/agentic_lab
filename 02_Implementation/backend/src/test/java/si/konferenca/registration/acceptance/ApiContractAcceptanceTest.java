package si.konferenca.registration.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * Contract-level behaviour from docs/contracts/openapi.yaml that is not tied to a single acceptance
 * criterion: client config, error shapes for malformed/oversized/wrong-type requests, health
 * endpoint.
 */
class ApiContractAcceptanceTest extends AcceptanceTestBase {

  @Test
  void clientConfigReportsTestMode() {
    Resp r = get("/api/config");

    assertThat(r.status()).isEqualTo(200);
    assertThat(r.json().path("recaptchaTestMode").asBoolean()).isTrue();
    assertThat(r.json().has("recaptchaSiteKey")).isTrue();
  }

  @Test
  void malformedJsonIsRejected() {
    Resp r = postJson("/api/registrations", "{\"type\": \"EXTERNAL\", ");

    assertThat(r.status()).isEqualTo(400);
    assertThat(r.header("Content-Type")).contains("application/problem+json");
    assertThat(r.json().path("code").asText()).isEqualTo("MALFORMED_REQUEST");
  }

  @Test
  void unknownPropertyIsRejected() {
    var body = validExternal(uniqueEmail("contract-unknown"));
    body.put("isAdmin", true);

    Resp r = register(body);

    assertThat(r.status()).isEqualTo(400);
    assertThat(r.json().path("code").asText()).isEqualTo("MALFORMED_REQUEST");
  }

  @Test
  void invalidRegistrationTypeIsRejected() {
    var body = validExternal(uniqueEmail("contract-type"));
    body.put("type", "VIP");

    Resp r = register(body);

    assertThat(r.status()).isEqualTo(400);
  }

  @Test
  void oversizedBodyIsRejected() {
    String email = uniqueEmail("contract-large");
    var body = validExternal(email);
    body.put("organization", "x".repeat(20 * 1024));

    Resp r = register(body);

    assertThat(r.status()).isEqualTo(413);
    assertThat(r.json().path("code").asText()).isEqualTo("PAYLOAD_TOO_LARGE");
    assertThat(countRegistrationsByEmail(email)).isZero();
  }

  @Test
  void nonJsonContentTypeIsRejected() {
    Resp r =
        send(
            HttpRequest.newBuilder(URI.create(url("/api/registrations")))
                .header("Content-Type", "text/plain")
                .POST(HttpRequest.BodyPublishers.ofString("hello", StandardCharsets.UTF_8))
                .build());

    assertThat(r.status()).isEqualTo(415);
    assertThat(r.json().path("code").asText()).isEqualTo("UNSUPPORTED_MEDIA_TYPE");
  }

  @Test
  void tooLongFieldIsRejected() {
    var body = validExternal(uniqueEmail("contract-long"));
    body.put("firstName", "A".repeat(101));

    assertRejectedWithFieldError(register(body), "firstName", "TOO_LONG");
  }

  @Test
  void controlCharactersAreRejected() {
    var body = validExternal(uniqueEmail("contract-ctrl"));
    body.put("lastName", "Novak\r\nBcc: victim@example.com");

    assertRejectedWithFieldError(register(body), "lastName", "INVALID_CHARACTERS");
  }

  @Test
  void missingRecaptchaTokenIsRejected() {
    var body = validExternal(uniqueEmail("contract-captcha"));
    body.remove("recaptchaToken");

    Resp r = register(body);

    assertThat(r.status()).isEqualTo(400);
    assertThat(r.json().path("code").asText()).isEqualTo("RECAPTCHA_FAILED");
  }

  @Test
  void apiResponsesCarrySecurityHeaders() {
    Resp r = get("/api/options");

    assertThat(r.header("X-Content-Type-Options")).isEqualTo("nosniff");
    assertThat(r.header("X-Frame-Options")).isEqualTo("DENY");
    assertThat(r.header("Content-Security-Policy")).contains("default-src 'none'");
  }

  @Test
  void healthEndpointIsUp() {
    Resp r = get("/actuator/health");

    assertThat(r.status()).isEqualTo(200);
    assertThat(r.json().path("status").asText()).isEqualTo("UP");
    assertThat(get("/actuator/health/readiness").status()).isEqualTo(200);
    assertThat(get("/actuator/health/liveness").status()).isEqualTo(200);
  }
}

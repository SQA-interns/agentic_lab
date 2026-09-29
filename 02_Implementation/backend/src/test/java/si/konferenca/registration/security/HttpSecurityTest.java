package si.konferenca.registration.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import si.konferenca.registration.support.PostgresIntegrationTest;

/** HTTP-level security controls of specification §8 beyond the acceptance suite. */
class HttpSecurityTest extends PostgresIntegrationTest {

  @DynamicPropertySource
  static void testMode(DynamicPropertyRegistry registry) {
    registerCommon(registry);
    registry.add("app.recaptcha.test-mode", () -> "true");
  }

  private static String organizerAuth() {
    return "Basic "
        + Base64.getEncoder()
            .encodeToString(
                (ORGANIZER_USER + ":" + ORGANIZER_PASSWORD).getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void restoreRejectsNonJsonSoCrossSiteFormsCannotTriggerIt() {
    Result r =
        send(
            request("/api/organizer/backups/restore")
                .header("Authorization", organizerAuth())
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("a=b")));

    assertThat(r.status()).isEqualTo(415);
  }

  @Test
  void restoreRejectsANonEmptyBody() {
    Result r =
        send(
            request("/api/organizer/backups/restore")
                .header("Authorization", organizerAuth())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"all\":true}")));

    assertThat(r.status()).isEqualTo(400);
    assertThat(r.body()).contains("MALFORMED_REQUEST");
  }

  @Test
  void unknownApiPathIsAProblemWithoutInternals() {
    Result r = send(request("/api/nothing-here").GET());

    assertThat(r.status()).isEqualTo(404);
    assertThat(r.body()).contains("NOT_FOUND").doesNotContain("Exception");
  }

  @Test
  void onlyHealthAndInfoActuatorEndpointsAreExposed() {
    assertThat(send(request("/actuator/env").GET()).status()).isEqualTo(404);
    assertThat(send(request("/actuator/beans").GET()).status()).isEqualTo(404);
    assertThat(send(request("/actuator/health").GET()).body()).doesNotContain("components");
  }

  @Test
  void rejectedResponsesAlsoCarrySecurityHeaders() {
    Result tooLarge =
        postJson("/api/registrations", "{\"firstName\":\"" + "x".repeat(20_000) + "\"}");

    assertThat(tooLarge.status()).isEqualTo(413);
    assertThat(tooLarge.raw().headers().firstValue("X-Content-Type-Options")).hasValue("nosniff");
    assertThat(tooLarge.raw().headers().firstValue("Cache-Control")).isPresent();
  }

  @Test
  void noSessionCookieIsIssuedToOrganizers() {
    Result r =
        send(
            request("/api/organizer/registrations/export")
                .header("Authorization", organizerAuth())
                .GET());

    assertThat(r.status()).isEqualTo(200);
    assertThat(r.raw().headers().allValues("Set-Cookie")).isEmpty();
  }

  @Test
  void corsPreflightFromAnotherOriginIsNotAllowed() {
    Result r =
        send(
            request("/api/registrations")
                .header("Origin", "https://evil.example")
                .header("Access-Control-Request-Method", "POST")
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody()));

    assertThat(r.raw().headers().firstValue("Access-Control-Allow-Origin")).isEmpty();
  }

  @Test
  void validationErrorsDoNotEchoSubmittedValues() {
    var body = externalRegistration("<script>alert(1)</script>", "test-pass");

    Result r = postJson("/api/registrations", body.toString());

    assertThat(r.status()).isEqualTo(400);
    assertThat(r.body()).doesNotContain("<script>");
  }
}

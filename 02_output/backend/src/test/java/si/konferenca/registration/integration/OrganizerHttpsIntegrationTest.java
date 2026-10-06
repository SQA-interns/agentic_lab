package si.konferenca.registration.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static si.konferenca.registration.integration.HttpSupport.basic;
import static si.konferenca.registration.integration.HttpSupport.request;
import static si.konferenca.registration.integration.HttpSupport.send;

import java.net.http.HttpResponse;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import si.konferenca.registration.acceptance.support.AcceptanceTestBase;
import si.konferenca.registration.acceptance.support.TestEnvironment;

/** SR-06: organizer credentials only over HTTPS when HTTPS-only access is on (the default). */
class OrganizerHttpsIntegrationTest extends AcceptanceTestBase {

  @Value("${local.server.port}")
  int port;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    configure(registry, Map.of("ORGANIZER_HTTPS_ONLY", "true"));
  }

  private static String organizer() {
    return basic(TestEnvironment.ORGANIZER_USERNAME, TestEnvironment.ORGANIZER_PASSWORD);
  }

  @Test
  void plainHttpExportIsForbiddenEvenWithValidCredentials() {
    HttpResponse<String> response =
        send(request(port, "/api/registrations/export").header("Authorization", organizer()).GET());

    assertThat(response.statusCode()).isEqualTo(403);
    assertThat(response.body()).contains("HTTPS required");
  }

  @Test
  void plainHttpExportWithoutCredentialsIsAlsoForbidden() {
    HttpResponse<String> response = send(request(port, "/api/registrations/export").GET());

    assertThat(response.statusCode()).isEqualTo(403);
  }

  @Test
  void exportForwardedAsHttpsByTheLocalProxyIsAllowed() {
    HttpResponse<String> response =
        send(
            request(port, "/api/registrations/export")
                .header("Authorization", organizer())
                .header("X-Forwarded-Proto", "https")
                .GET());

    assertThat(response.statusCode()).isEqualTo(200);
  }

  @Test
  void forwardedHttpsStillNeedsValidCredentials() {
    HttpResponse<String> response =
        send(
            request(port, "/api/registrations/export")
                .header(
                    "Authorization",
                    basic(TestEnvironment.ORGANIZER_USERNAME, "wrong-password-0000"))
                .header("X-Forwarded-Proto", "https")
                .GET());

    assertThat(response.statusCode()).isEqualTo(401);
  }

  @Test
  void publicEndpointsStayAvailableOverPlainHttp() {
    assertThat(send(request(port, "/api/form-config").GET()).statusCode()).isEqualTo(200);
  }
}

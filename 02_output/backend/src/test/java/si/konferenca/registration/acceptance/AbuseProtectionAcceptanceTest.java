package si.konferenca.registration.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import si.konferenca.registration.RegistrationApplication;
import si.konferenca.registration.acceptance.support.Api;
import si.konferenca.registration.acceptance.support.Db;
import si.konferenca.registration.acceptance.support.Fixtures;
import si.konferenca.registration.acceptance.support.TestInfrastructure;

/** SR-03, SB-06: rate limits on registration and export, and the request-size limit. */
@SpringBootTest(
    classes = RegistrationApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AbuseProtectionAcceptanceTest {

  @LocalServerPort int port;

  private Api api;
  private final Db db = TestInfrastructure.db();

  @DynamicPropertySource
  static void configuration(DynamicPropertyRegistry registry) {
    TestInfrastructure.register(
        registry,
        Map.of(
            "app.json-copy-dir", TestInfrastructure.newTempDir("abuse-copies").toString(),
            "app.rate-limit.registration-per-10-min", "3",
            "app.rate-limit.export-per-min", "2",
            "app.max-request-bytes", "4096"));
  }

  @BeforeEach
  void createClient() {
    api = new Api(port);
  }

  @Test
  @DisplayName("SR-03 registrations above the rate limit are refused and not stored")
  void sr03RegistrationRateLimit() {
    String client = "X-Forwarded-For";
    String ip = "198.51.100.23";
    for (int i = 0; i < 3; i++) {
      Api.Response ok =
          api.postJson("/api/registrations", Fixtures.external(Fixtures.uniqueEmail()), client, ip);
      assertThat(ok.status()).as(ok.text()).isEqualTo(201);
    }
    String email = Fixtures.uniqueEmail();

    Api.Response limited = api.postJson("/api/registrations", Fixtures.external(email), client, ip);

    assertThat(limited.status()).isEqualTo(429);
    assertThat(db.countRegistrations(email)).isZero();
    Api.Response otherClient =
        api.postJson(
            "/api/registrations",
            Fixtures.external(Fixtures.uniqueEmail()),
            client,
            "198.51.100.99");
    assertThat(otherClient.status()).as(otherClient.text()).isEqualTo(201);
  }

  @Test
  @DisplayName("SR-03 SB-06 export attempts above the rate limit are refused")
  void sr03ExportRateLimit() {
    String wrong = Api.basicAuth(TestInfrastructure.ORGANIZER_USERNAME, "guess-password-000000");
    String[] headers = {
      "Authorization", wrong, "X-Forwarded-For", "198.51.100.40", "X-Forwarded-Proto", "https"
    };
    assertThat(api.get("/api/admin/registrations/export", headers).status()).isEqualTo(401);
    assertThat(api.get("/api/admin/registrations/export", headers).status()).isEqualTo(401);

    Api.Response limited = api.get("/api/admin/registrations/export", headers);

    assertThat(limited.status()).isEqualTo(429);
  }

  @Test
  @DisplayName("SR-03 a request body above the size limit is refused and not stored")
  void sr03RequestSizeLimit() {
    String email = Fixtures.uniqueEmail();
    Map<String, Object> request = Fixtures.external(email);
    request.put("organization", "x".repeat(5000));
    byte[] body = Api.JSON.writeValueAsString(request).getBytes(StandardCharsets.UTF_8);

    Api.Response r = api.postRaw("/api/registrations", body, "X-Forwarded-For", "198.51.100.77");

    assertThat(r.status()).isEqualTo(413);
    assertThat(db.countRegistrations(email)).isZero();
  }
}

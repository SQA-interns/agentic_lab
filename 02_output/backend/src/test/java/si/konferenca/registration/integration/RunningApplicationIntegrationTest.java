package si.konferenca.registration.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import si.konferenca.registration.acceptance.support.AcceptanceEnvironment;
import si.konferenca.registration.acceptance.support.Registrations;
import si.konferenca.registration.acceptance.support.RunningApp;
import si.konferenca.registration.acceptance.support.RunningApp.Response;

/** Security headers, error paths and limits of the running backend (SB-10, SR-03, KP-02). */
class RunningApplicationIntegrationTest {

  private static RunningApp app;

  @BeforeAll
  static void start() {
    Map<String, String> config = AcceptanceEnvironment.defaultConfiguration();
    config.put("RATE_LIMIT_OPTIONS_PER_MINUTE", "3");
    app = RunningApp.start(config);
  }

  @AfterAll
  static void stop() {
    app.close();
  }

  private static void assertSecurityHeadersOnce(Response r) {
    for (String h :
        List.of(
            "Content-Security-Policy",
            "X-Content-Type-Options",
            "X-Frame-Options",
            "Referrer-Policy")) {
      List<String> values =
          r.headers().entrySet().stream()
              .filter(e -> e.getKey().equalsIgnoreCase(h))
              .flatMap(e -> e.getValue().stream())
              .toList();
      assertThat(values).as(h).hasSize(1);
    }
    assertThat(r.header("Content-Security-Policy")).contains("default-src 'none'");
    assertThat(r.header("X-Content-Type-Options")).isEqualTo("nosniff");
    assertThat(r.header("X-Frame-Options")).isEqualTo("DENY");
  }

  @Test
  void everyApiResponseCarriesEachSecurityHeaderOnce() {
    assertSecurityHeadersOnce(app.register(Registrations.external()));
    assertSecurityHeadersOnce(app.export(null, null));
    assertSecurityHeadersOnce(app.exportAsOrganizer());
    assertSecurityHeadersOnce(app.postJson("/api/registrations", "{"));
  }

  @Test
  void unknownApiPathsAreRefusedWithProblemJson() {
    Response r = app.get("/api/registrations");

    assertThat(r.status()).isEqualTo(401);
    assertThat(r.header("Content-Type")).startsWith("application/problem+json");
    assertThat(app.get("/actuator/env").status()).isEqualTo(401);
  }

  @Test
  void healthEndpointsAreUpWithoutDetails() {
    Response r = app.get("/actuator/health/readiness");

    assertThat(r.status()).isEqualTo(200);
    assertThat(r.text()).isEqualTo("{\"status\":\"UP\"}");
    assertThat(app.get("/actuator/health/liveness").status()).isEqualTo(200);
  }

  @Test
  void wrongContentTypeIsUnsupported() throws Exception {
    HttpRequest form =
        HttpRequest.newBuilder(URI.create(app.baseUrl() + "/api/registrations"))
            .header("Content-Type", "text/plain")
            .POST(HttpRequest.BodyPublishers.ofString("x"))
            .build();

    HttpResponse<String> r =
        HttpClient.newHttpClient().send(form, HttpResponse.BodyHandlers.ofString());

    assertThat(r.statusCode()).isEqualTo(415);
    assertThat(r.body()).contains("UNSUPPORTED_MEDIA_TYPE");
  }

  @Test
  void optionsAreRateLimitedPerAddress() {
    int limited = 0;
    for (int i = 0; i < 6; i++) {
      Response r = app.get("/api/options");
      if (r.status() == 429) {
        limited++;
        assertThat(r.header("Retry-After")).isNotBlank();
        assertThat(r.header("Content-Type")).startsWith("application/problem+json");
      }
    }
    assertThat(limited).isGreaterThanOrEqualTo(3);
  }

  @Test
  void flywayMigrationIsTheDatabaseContract() throws Exception {
    String migration =
        Files.readString(Path.of("src/main/resources/db/migration/V1__create_registration.sql"));
    String contract = Files.readString(Path.of("../docs/02_contracts/database.sql"));

    assertThat(migration).isEqualTo(contract);
  }
}

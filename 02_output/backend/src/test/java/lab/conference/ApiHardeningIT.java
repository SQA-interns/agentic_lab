package lab.conference;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import lab.conference.acceptance.support.AppInstance;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Integration: headers, origin/content-type checks, actuator exposure, error bodies (SB-07, SB-10).
 */
class ApiHardeningIT {

  private static AppInstance app;
  private static final HttpClient CLIENT = HttpClient.newHttpClient();

  @BeforeAll
  static void start() {
    app = AppInstance.builder().build().start();
  }

  @AfterAll
  static void stop() {
    app.stop();
  }

  private static HttpResponse<String> send(HttpRequest.Builder b) throws Exception {
    return CLIENT.send(b.build(), HttpResponse.BodyHandlers.ofString());
  }

  private static HttpRequest.Builder req(String path) {
    return HttpRequest.newBuilder(URI.create(app.baseUrl() + path));
  }

  @Test
  void securityHeadersArePresent() throws Exception {
    HttpResponse<String> r = send(req("/api/catalog"));
    assertThat(r.statusCode()).isEqualTo(200);
    assertThat(r.headers().firstValue("X-Content-Type-Options")).contains("nosniff");
    assertThat(r.headers().firstValue("X-Frame-Options")).contains("DENY");
    assertThat(r.headers().firstValue("Content-Security-Policy"))
        .contains("default-src 'none'; frame-ancestors 'none'");
    assertThat(r.headers().firstValue("Referrer-Policy")).contains("no-referrer");
    assertThat(r.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
    assertThat(r.headers().firstValue("Set-Cookie")).isEmpty();
  }

  @Test
  void foreignOriginNonJsonAndMalformedBodiesAreRejected() throws Exception {
    HttpResponse<String> origin =
        send(
            req("/api/registrations/external")
                .header("Origin", "https://evil.test")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{}")));
    assertThat(origin.statusCode()).isEqualTo(403);
    HttpResponse<String> text =
        send(
            req("/api/registrations/external")
                .header("Content-Type", "text/plain")
                .POST(HttpRequest.BodyPublishers.ofString("{}")));
    assertThat(text.statusCode()).isEqualTo(415);
    HttpResponse<String> malformed =
        send(
            req("/api/registrations/student")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"a\":")));
    assertThat(malformed.statusCode()).isEqualTo(400);
    assertThat(malformed.body())
        .contains("MALFORMED_REQUEST")
        .doesNotContain("Exception", "jackson");
    HttpResponse<String> trailing =
        send(
            req("/api/registrations/student")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{} {}")));
    assertThat(trailing.statusCode()).isEqualTo(400);
  }

  @Test
  void unknownPathsAndMethodsGiveGenericProblems() throws Exception {
    HttpResponse<String> missing = send(req("/api/nothing"));
    assertThat(missing.statusCode()).isEqualTo(404);
    assertThat(missing.body()).doesNotContain("trace", "Exception");
    HttpResponse<String> wrongForm =
        send(
            req("/api/registrations/vip")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{}")));
    assertThat(wrongForm.statusCode()).isEqualTo(404);
    HttpResponse<String> method = send(req("/api/catalog").DELETE());
    assertThat(method.statusCode()).isIn(403, 404, 405);
  }

  @Test
  void onlyHealthProbesAreExposed() throws Exception {
    assertThat(send(req("/actuator/health/readiness")).body()).contains("UP").doesNotContain("db");
    assertThat(send(req("/actuator/health/liveness")).statusCode()).isEqualTo(200);
    assertThat(send(req("/actuator/env")).statusCode()).isIn(401, 403, 404);
    assertThat(send(req("/actuator")).statusCode()).isIn(401, 403, 404);
  }

  @Test
  void exportIsNeverCachedAndBasicRealmIsAnnounced() throws Exception {
    HttpResponse<String> denied = send(req("/api/organizer/export.xlsx"));
    assertThat(denied.statusCode()).isEqualTo(401);
    assertThat(denied.headers().firstValue("WWW-Authenticate").orElse(""))
        .contains("Lab Conference organizer");
    assertThat(denied.headers().firstValue("Content-Type").orElse("")).contains("problem+json");
  }
}

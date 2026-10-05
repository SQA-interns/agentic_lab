package si.konferenca.registration.integration;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import si.konferenca.registration.RegistrationApplication;
import si.konferenca.registration.acceptance.support.AcceptanceEnvironment;
import si.konferenca.registration.acceptance.support.ApiClient;
import si.konferenca.registration.acceptance.support.Registrations;

/** HTTP-level controls: SB-10, SR-03, SR-06, ES-07, ES-09 and CORS (specification section 6). */
class HttpSecurityIntegrationTest {

  private static final String ORIGIN = "http://localhost:5173";
  private static ConfigurableApplicationContext app;
  private static ApiClient api;
  private static int port;
  private final HttpClient http = HttpClient.newHttpClient();

  @BeforeAll
  static void start() throws Exception {
    Map<String, Object> properties =
        AcceptanceEnvironment.propertiesFor(
            AcceptanceEnvironment.jdbcUrl(),
            Files.createTempDirectory("json-http-security"),
            AcceptanceEnvironment.OPTIONS_FILE);
    properties.put("RATE_LIMIT_REGISTRATIONS", "3");
    properties.put("MAX_REQUEST_BYTES", "2000");
    properties.put("CORS_ALLOWED_ORIGIN", ORIGIN);
    app =
        new SpringApplicationBuilder(RegistrationApplication.class)
            .profiles("test")
            .properties(properties)
            .run("--server.port=0");
    port = app.getEnvironment().getRequiredProperty("local.server.port", Integer.class);
    api = new ApiClient(port);
  }

  @AfterAll
  static void stop() {
    app.close();
  }

  private HttpResponse<String> send(HttpRequest.Builder request) throws Exception {
    return http.send(request.build(), HttpResponse.BodyHandlers.ofString(UTF_8));
  }

  private static URI uri(String path) {
    return URI.create("http://127.0.0.1:" + port + path);
  }

  @Test
  void responsesCarrySecurityHeaders() {
    ApiClient.Response response = api.formConfig();

    assertThat(response.status()).isEqualTo(200);
    assertThat(response.header("Content-Security-Policy"))
        .isEqualTo("default-src 'none'; frame-ancestors 'none'");
    assertThat(response.header("X-Frame-Options")).isEqualTo("DENY");
    assertThat(response.header("X-Content-Type-Options")).isEqualTo("nosniff");
    assertThat(response.header("Referrer-Policy")).isEqualTo("no-referrer");
    assertThat(response.header("Cache-Control")).contains("no-store");
    assertThat(response.header("Set-Cookie")).isEmpty();
  }

  @Test
  void healthIsPublicAndOtherActuatorEndpointsAreDenied() {
    assertThat(api.get("/actuator/health/liveness").text()).contains("UP");
    assertThat(api.get("/actuator/health/readiness").text()).contains("UP");
    assertThat(api.get("/actuator/env").status()).isIn(401, 403, 404);
    assertThat(api.get("/api/unknown").status()).isIn(401, 403, 404);
  }

  @Test
  void malformedAndUnsupportedBodiesAreRejectedWithoutInternals() throws Exception {
    ApiClient.Response broken = api.postJson("/api/registrations", "{\"type\":");
    assertThat(broken.status()).isEqualTo(400);
    assertThat(broken.json().get("code").asString()).isEqualTo("MALFORMED_REQUEST");
    assertThat(broken.text()).doesNotContain("Exception").doesNotContain("tools.jackson");

    ApiClient.Response unknownField =
        api.postJson("/api/registrations", Registrations.external().put("admin", true));
    assertThat(unknownField.status()).isEqualTo(400);
    assertThat(unknownField.json().get("code").asString()).isEqualTo("MALFORMED_REQUEST");

    HttpResponse<String> text =
        send(
            HttpRequest.newBuilder(uri("/api/registrations"))
                .header("Content-Type", "text/plain")
                .POST(HttpRequest.BodyPublishers.ofString("hello")));
    assertThat(text.statusCode()).isEqualTo(415);
    assertThat(text.body()).contains("UNSUPPORTED_MEDIA_TYPE");
  }

  @Test
  void oversizedBodyIsRejected() {
    ApiClient.Response response =
        api.postJson("/api/organizer/token", "{\"username\":\"" + "x".repeat(3000) + "\"}");

    assertThat(response.status()).isEqualTo(413);
    assertThat(response.json().get("code").asString()).isEqualTo("PAYLOAD_TOO_LARGE");
  }

  @Test
  void registrationsAreRateLimitedPerClient() {
    String client = "198.51.100.77";
    int limited = 0;
    for (int i = 0; i < 5; i++) {
      ApiClient.Response response =
          api.postJson("/api/registrations", Registrations.external(), "X-Forwarded-For", client);
      if (response.status() == 429) {
        limited++;
        assertThat(response.header("Retry-After")).isEqualTo("60");
      }
    }
    assertThat(limited).isEqualTo(2);
    assertThat(
            api.postJson(
                    "/api/registrations",
                    Registrations.external(),
                    "X-Forwarded-For",
                    "198.51.100.78")
                .status())
        .isEqualTo(201);
  }

  @Test
  void organizerCredentialsAreRefusedOverPlainHttpFromRemoteClients() {
    String body =
        "{\"username\":\""
            + AcceptanceEnvironment.ORGANIZER_USERNAME
            + "\",\"password\":\""
            + AcceptanceEnvironment.ORGANIZER_PASSWORD
            + "\"}";

    ApiClient.Response plain =
        api.postJson("/api/organizer/token", body, "X-Forwarded-For", "203.0.113.9");
    assertThat(plain.status()).isEqualTo(403);
    assertThat(plain.json().get("code").asString()).isEqualTo("HTTPS_REQUIRED");
    ApiClient.Response export =
        api.get("/api/organizer/registrations/export", "X-Forwarded-For", "203.0.113.9");
    assertThat(export.status()).isEqualTo(403);

    ApiClient.Response secure =
        api.postJson(
            "/api/organizer/token",
            body,
            "X-Forwarded-For",
            "203.0.113.9",
            "X-Forwarded-Proto",
            "https");
    assertThat(secure.status()).isEqualTo(200);
    assertThat(secure.json().get("expiresAt").asString()).isNotBlank();
  }

  @Test
  void tokenEndpointRejectsMalformedCredentialsAndChallengesForBearer() {
    assertThat(api.postJson("/api/organizer/token", "{\"username\":\"a\"}").status())
        .isEqualTo(400);
    assertThat(api.postJson("/api/organizer/token", "{\"username\":\"a\",\"password\":1}").status())
        .isEqualTo(400);
    assertThat(
            api.postJson("/api/organizer/token", "{\"username\":\"a\",\"password\":\"b\",\"c\":1}")
                .status())
        .isEqualTo(400);

    ApiClient.Response basic =
        api.get("/api/organizer/registrations/export", "Authorization", "Basic b3JnOnB3");
    assertThat(basic.status()).isEqualTo(401);
    assertThat(basic.header("WWW-Authenticate")).isEqualTo("Bearer");
    assertThat(basic.json().get("code").asString()).isEqualTo("UNAUTHORIZED");
  }

  @Test
  void corsAllowsOnlyTheConfiguredOrigin() throws Exception {
    HttpResponse<String> allowed =
        send(
            HttpRequest.newBuilder(uri("/api/registrations"))
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .header("Origin", ORIGIN)
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "content-type"));
    assertThat(allowed.headers().firstValue("Access-Control-Allow-Origin")).contains(ORIGIN);

    HttpResponse<String> other =
        send(
            HttpRequest.newBuilder(uri("/api/registrations"))
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .header("Origin", "https://evil.example")
                .header("Access-Control-Request-Method", "POST"));
    assertThat(other.statusCode()).isEqualTo(403);
    assertThat(other.headers().firstValue("Access-Control-Allow-Origin")).isEmpty();
  }
}

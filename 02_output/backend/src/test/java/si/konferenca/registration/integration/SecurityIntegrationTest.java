package si.konferenca.registration.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import si.konferenca.registration.RegistrationApplication;

/**
 * Security controls through the API (specification section 6): HTTPS-only organizer access, limits,
 * headers, error answers and what the backend exposes.
 */
class SecurityIntegrationTest {

  private static final String EXPORT = "/api/registrations/export";
  private static final String BASIC =
      "Basic "
          + Base64.getEncoder()
              .encodeToString(
                  (IntegrationApp.ORGANIZER_USERNAME + ":" + IntegrationApp.ORGANIZER_PASSWORD)
                      .getBytes(StandardCharsets.UTF_8));

  private static IntegrationApp app;

  /** HTTPS required, behind a trusted proxy, with small limits. */
  @BeforeAll
  static void start() {
    app =
        IntegrationApp.start(
            Map.of(
                "ORGANIZER_REQUIRE_HTTPS", "true",
                "TRUST_FORWARDED_HEADERS", "true",
                "RATE_LIMIT_REGISTRATION", "4",
                "MAX_REQUEST_BYTES", "600",
                "CORS_ALLOWED_ORIGIN", "http://localhost:5173"));
  }

  @AfterAll
  static void stop() {
    app.close();
  }

  private static String header(HttpResponse<String> reply, String name) {
    return reply.headers().firstValue(name).orElse("");
  }

  @Test
  void sr06_organizerCredentialsOverPlainHttpAreRefusedWithoutAChallenge() {
    HttpResponse<String> reply = app.get(EXPORT, "Authorization", BASIC);

    assertThat(reply.statusCode()).isEqualTo(403);
    assertThat(header(reply, "WWW-Authenticate")).isEmpty();
    assertThat(header(reply, "Content-Type")).startsWith("application/problem+json");
  }

  @Test
  void sr06_exportOverHttpsAtTheTrustedProxyIsAllowed() {
    HttpResponse<String> reply =
        app.get(EXPORT, "Authorization", BASIC, "X-Forwarded-Proto", "https");

    assertThat(reply.statusCode()).isEqualTo(200);
    assertThat(header(reply, "Cache-Control")).contains("no-store");
  }

  @Test
  void br08_exportOverHttpsWithoutCredentialsIsChallenged() {
    HttpResponse<String> reply = app.get(EXPORT, "X-Forwarded-Proto", "https");

    assertThat(reply.statusCode()).isEqualTo(401);
    assertThat(header(reply, "WWW-Authenticate")).startsWith("Basic");
    assertThat(reply.body()).contains("\"status\":401");
  }

  @Test
  void sb10_apiAnswersCarryTheSecurityHeaders() {
    HttpResponse<String> reply = app.get("/api/options");

    assertThat(reply.statusCode()).isEqualTo(200);
    assertThat(header(reply, "Content-Security-Policy"))
        .isEqualTo("default-src 'none'; frame-ancestors 'none'");
    assertThat(header(reply, "X-Content-Type-Options")).isEqualTo("nosniff");
    assertThat(header(reply, "X-Frame-Options")).isEqualTo("DENY");
    assertThat(header(reply, "Referrer-Policy")).isEqualTo("no-referrer");
    assertThat(header(reply, "Cache-Control")).contains("no-store");
    assertThat(header(reply, "Set-Cookie")).isEmpty();
  }

  @Test
  void corsAllowsOnlyTheConfiguredOrigin() {
    HttpResponse<String> allowed = app.get("/api/options", "Origin", "http://localhost:5173");
    HttpResponse<String> other = app.get("/api/options", "Origin", "https://evil.example");

    assertThat(header(allowed, "Access-Control-Allow-Origin")).isEqualTo("http://localhost:5173");
    assertThat(other.statusCode()).isEqualTo(403);
    assertThat(header(other, "Access-Control-Allow-Origin")).isEmpty();
  }

  @Test
  void sr03_bodyOverTheSizeLimitIsRefused() {
    String padded = "{\"type\":\"EXTERNAL\",\"firstName\":\"" + "a".repeat(700) + "\"}";

    HttpResponse<String> reply = app.post("/api/registrations", "application/json", padded);

    assertThat(reply.statusCode()).isEqualTo(413);
    assertThat(header(reply, "Content-Type")).startsWith("application/problem+json");
  }

  @Test
  void bodyThatIsNotJsonIsUnsupported() {
    HttpResponse<String> reply = app.post("/api/registrations", "text/plain", "type=EXTERNAL");

    assertThat(reply.statusCode()).isEqualTo(415);
    assertThat(reply.body()).contains("\"status\":415");
  }

  @Test
  void sr03_registrationsOverTheRateLimitAreRefused() {
    // The limit of this instance is 4 per minute and client; the client is taken from the
    // header of the trusted proxy, so other tests of this class are not affected.
    awaitStartOfAWindow();
    for (int i = 0; i < 4; i++) {
      assertThat(postFrom("203.0.113.77").statusCode()).isEqualTo(400);
    }

    HttpResponse<String> refused = postFrom("203.0.113.77");

    assertThat(refused.statusCode()).isEqualTo(429);
    assertThat(Integer.parseInt(header(refused, "Retry-After"))).isBetween(1, 60);
    assertThat(header(refused, "Content-Type")).startsWith("application/problem+json");
    // Reading is a separate group, and another client has its own count.
    assertThat(app.get("/api/form-config", "X-Forwarded-For", "203.0.113.77").statusCode())
        .isEqualTo(200);
    assertThat(postFrom("203.0.113.78").statusCode()).isEqualTo(400);
  }

  /** Windows are whole minutes; the requests of the test must not straddle two of them. */
  private static void awaitStartOfAWindow() {
    while (System.currentTimeMillis() / 1000 % 60 >= 50) {
      try {
        Thread.sleep(500);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException(e);
      }
    }
  }

  private static HttpResponse<String> postFrom(String client) {
    return app.post("/api/registrations", "application/json", "{}", "X-Forwarded-For", client);
  }

  @Test
  void es09_onlyHealthIsExposedAndItHoldsNoDetails() {
    assertThat(app.get("/actuator/health/readiness").statusCode()).isEqualTo(200);
    assertThat(app.get("/actuator/health/liveness").statusCode()).isEqualTo(200);
    HttpResponse<String> health = app.get("/actuator/health");
    assertThat(health.body()).contains("\"status\":\"UP\"").doesNotContain("PostgreSQL", "jdbc");
    assertThat(app.get("/actuator/env").statusCode()).isEqualTo(404);
    assertThat(app.get("/actuator/beans").statusCode()).isEqualTo(404);
  }

  @Test
  void sb07_unknownPathsAndMethodsAnswerWithFixedProblemTexts() {
    HttpResponse<String> notFound = app.get("/api/unknown");
    HttpResponse<String> wrongMethod = app.get("/api/registrations");

    assertThat(notFound.statusCode()).isEqualTo(404);
    assertThat(wrongMethod.statusCode()).isEqualTo(405);
    for (HttpResponse<String> reply : new HttpResponse[] {notFound, wrongMethod}) {
      assertThat(reply.body()).doesNotContain("Exception", "springframework", "trace");
    }
  }

  @Test
  void sr05_lineBreakInANameIsRejectedSoItCannotReachAnEmail() {
    HttpResponse<String> reply =
        app.post(
            "/api/registrations",
            "application/json",
            """
            {"type":"EXTERNAL","firstName":"Ana\\r\\nBcc: x@example.org","lastName":"Novak",
             "email":"ana@example.org","organization":"P","optionIds":[],"consent":true,
             "captchaToken":"test-pass"}
            """);

    assertThat(reply.statusCode()).isEqualTo(400);
    assertThat(reply.body()).contains("\"field\":\"firstName\"", "\"code\":\"invalid_characters\"");
  }

  @Test
  void sr02_productionRefusesToStartInTestModeOrWithoutKeys() {
    Map<String, String> testModeInProduction =
        IntegrationApp.settings(
            Map.of("APP_ENVIRONMENT", "production", "ORGANIZER_REQUIRE_HTTPS", "true"));
    Map<String, String> noKeysInProduction =
        IntegrationApp.settings(
            Map.of(
                "APP_ENVIRONMENT", "production",
                "ORGANIZER_REQUIRE_HTTPS", "true",
                "RECAPTCHA_TEST_MODE", "false"));

    for (Map<String, String> settings : new Map[] {testModeInProduction, noKeysInProduction}) {
      String[] args =
          settings.entrySet().stream()
              .map(entry -> "--" + entry.getKey() + "=" + entry.getValue())
              .toArray(String[]::new);
      assertThatThrownBy(() -> new SpringApplication(RegistrationApplication.class).run(args))
          .hasRootCauseInstanceOf(IllegalStateException.class)
          .rootCause()
          .hasMessageContaining("Invalid configuration");
    }
  }
}

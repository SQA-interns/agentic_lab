package si.konferenca.registration.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import si.konferenca.registration.Application;
import si.konferenca.registration.support.PostgresIntegrationTest;

/**
 * reCAPTCHA production mode (specification §8.2, DoD §3): real server-side verification against a
 * mocked siteverify endpoint, plus the startup guards for missing keys.
 */
class RecaptchaProductionModeTest extends PostgresIntegrationTest {

  private static final AtomicInteger VERIFY_CALLS = new AtomicInteger();
  private static final HttpServer SITEVERIFY = startSiteverify();

  /** Answers success only for the response token "human", like Google does for a solved widget. */
  private static HttpServer startSiteverify() {
    try {
      HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext(
          "/recaptcha/api/siteverify",
          exchange -> {
            VERIFY_CALLS.incrementAndGet();
            String form =
                new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            boolean ok = form.contains("secret=prod-secret") && form.contains("response=human");
            byte[] body = ("{\"success\":" + ok + "}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
              out.write(body);
            }
          });
      server.start();
      return server;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static String verifyUrl() {
    return "http://127.0.0.1:" + SITEVERIFY.getAddress().getPort() + "/recaptcha/api/siteverify";
  }

  @DynamicPropertySource
  static void productionMode(DynamicPropertyRegistry registry) {
    registerCommon(registry);
    registry.add("app.recaptcha.test-mode", () -> "false");
    registry.add("app.recaptcha.site-key", () -> "prod-site-key");
    registry.add("app.recaptcha.secret-key", () -> "prod-secret");
    registry.add("app.recaptcha.verify-url", RecaptchaProductionModeTest::verifyUrl);
  }

  @Test
  void clientConfigExposesTheSiteKeyAndProductionMode() {
    Result r = send(request("/api/config").GET());

    assertThat(r.status()).isEqualTo(200);
    assertThat(r.body())
        .contains("\"recaptchaSiteKey\":\"prod-site-key\"")
        .contains("\"recaptchaTestMode\":false");
  }

  @Test
  void tokenAcceptedByTheProviderRegisters() {
    int before = VERIFY_CALLS.get();

    Result r =
        postJson("/api/registrations", externalRegistration(uniqueEmail(), "human").toString());

    assertThat(r.status()).as(r.body()).isEqualTo(201);
    assertThat(VERIFY_CALLS.get()).isEqualTo(before + 1);
  }

  @Test
  void tokenRejectedByTheProviderIsRefused() {
    String email = uniqueEmail();

    Result r = postJson("/api/registrations", externalRegistration(email, "bot").toString());

    assertThat(r.status()).isEqualTo(400);
    assertThat(r.body()).contains("RECAPTCHA_FAILED");
    assertThat(countByEmail(email)).isZero();
  }

  @Test
  void theTestModeTokenIsWorthlessInProductionMode() {
    String email = uniqueEmail();

    Result r = postJson("/api/registrations", externalRegistration(email, "test-pass").toString());

    assertThat(r.status()).isEqualTo(400);
    assertThat(countByEmail(email)).isZero();
  }

  private static SpringApplicationBuilder app(String... extra) {
    String[] base = {
      "server.port=0",
      "spring.datasource.url=" + POSTGRES.getJdbcUrl(),
      "spring.datasource.username=" + POSTGRES.getUsername(),
      "spring.datasource.password=" + POSTGRES.getPassword(),
      "app.options.file=" + OPTIONS_FILE,
      "app.backup.dir=" + BACKUP_DIR,
      "app.organizer.emails=org@example.si",
      "app.organizer.username=" + ORGANIZER_USER,
    };
    String[] all = new String[base.length + extra.length];
    System.arraycopy(base, 0, all, 0, base.length);
    System.arraycopy(extra, 0, all, base.length, extra.length);
    return new SpringApplicationBuilder(Application.class).properties(all);
  }

  @Test
  void productionModeWithoutKeysRefusesToStart() {
    assertThatThrownBy(
            () ->
                app("app.organizer.password=" + ORGANIZER_PASSWORD, "app.recaptcha.test-mode=false")
                    .run()
                    .close())
        .hasStackTraceContaining("reCAPTCHA production mode requires");
  }

  @Test
  void productionModeIsTheDefault() {
    assertThatThrownBy(() -> app("app.organizer.password=" + ORGANIZER_PASSWORD).run().close())
        .hasStackTraceContaining("reCAPTCHA production mode requires");
  }

  @Test
  void shortOrganizerPasswordRefusesToStart() {
    assertThatThrownBy(
            () -> app("app.organizer.password=short", "app.recaptcha.test-mode=true").run().close())
        .hasStackTraceContaining("at least 16 characters");
  }

  @Test
  void missingOrganizerRecipientsRefuseToStart() {
    assertThatThrownBy(
            () ->
                app(
                        "app.organizer.password=" + ORGANIZER_PASSWORD,
                        "app.recaptcha.test-mode=true",
                        "app.organizer.emails=")
                    .run()
                    .close())
        .isInstanceOf(Exception.class);
  }
}

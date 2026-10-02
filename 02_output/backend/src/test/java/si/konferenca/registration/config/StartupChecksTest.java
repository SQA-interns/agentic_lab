package si.konferenca.registration.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import si.konferenca.registration.config.AppProperties.Organizer;
import si.konferenca.registration.config.AppProperties.RateLimit;
import si.konferenca.registration.config.AppProperties.Recaptcha;

/** Settings the backend refuses to start with (SR-02, SR-06, specification section 5). */
class StartupChecksTest {

  private static final String ORGANIZER_SECRET = "only-for-this-unit-test";

  private static AppProperties properties(
      String environment, Recaptcha recaptcha, Organizer organizer, String organizerEmails) {
    return new AppProperties(
        environment,
        "prijave@konferenca.example",
        "Konferenca",
        organizerEmails,
        "/config/options.json",
        "Soglašam.",
        "/data/registrations",
        recaptcha,
        organizer,
        "",
        new RateLimit(10, 10, 120),
        16384,
        false);
  }

  private static Recaptcha liveKeys() {
    return new Recaptcha(false, "site-key", "secret-key", "https://example.org/verify");
  }

  private static Organizer organizer(boolean requireHttps) {
    return new Organizer("organizer", ORGANIZER_SECRET, requireHttps);
  }

  @Test
  void productionWithKeysAndHttpsMayStart() {
    AppProperties properties =
        properties("production", liveKeys(), organizer(true), "a@example.org, b@example.org");

    assertThat(StartupChecks.violations(properties)).isEmpty();
    StartupChecks.verify(properties);
  }

  @Test
  void sr02_productionRefusesTheTestMode() {
    AppProperties properties =
        properties(
            "production",
            new Recaptcha(true, "site-key", "secret-key", "https://example.org/verify"),
            organizer(true),
            "a@example.org");

    assertThat(StartupChecks.violations(properties))
        .containsExactly("RECAPTCHA_TEST_MODE must be off in production");
    assertThatThrownBy(() -> StartupChecks.verify(properties))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("RECAPTCHA_TEST_MODE");
  }

  @Test
  void sr02_productionRefusesEmptyKeys() {
    for (Recaptcha recaptcha :
        new Recaptcha[] {
          new Recaptcha(false, "", "secret-key", "u"),
          new Recaptcha(false, "site-key", " ", "u"),
          new Recaptcha(false, null, null, "u")
        }) {
      assertThat(
              StartupChecks.violations(
                  properties("production", recaptcha, organizer(true), "a@example.org")))
          .containsExactly("RECAPTCHA_SITE_KEY and RECAPTCHA_SECRET_KEY must be set in production");
    }
  }

  @Test
  void sr06_productionRefusesOrganizerAccessOverPlainHttp() {
    assertThat(
            StartupChecks.violations(
                properties("production", liveKeys(), organizer(false), "a@example.org")))
        .containsExactly("ORGANIZER_REQUIRE_HTTPS must be on in production");
  }

  @Test
  void localMayUseTheTestModeWithoutKeysAndPlainHttp() {
    AppProperties properties =
        properties("local", new Recaptcha(true, "", "", "u"), organizer(false), "a@example.org");

    assertThat(StartupChecks.violations(properties)).isEmpty();
  }

  @Test
  void anyEnvironmentWithoutTestModeNeedsTheKeys() {
    AppProperties properties =
        properties("local", new Recaptcha(false, "", "", "u"), organizer(false), "a@example.org");

    assertThat(StartupChecks.violations(properties))
        .containsExactly("without RECAPTCHA_TEST_MODE the reCAPTCHA keys must be set");
  }

  @Test
  void organizerAccountAndRecipientsAreRequiredAndValuesAreNeverNamed() {
    AppProperties properties =
        properties(
            "local",
            new Recaptcha(true, "", "", "u"),
            new Organizer(" ", ORGANIZER_SECRET, false),
            " , ");

    assertThat(StartupChecks.violations(properties))
        .containsExactly(
            "ORGANIZER_USERNAME and ORGANIZER_PASSWORD must be set",
            "ORGANIZER_EMAILS must name at least one recipient");
    assertThatThrownBy(() -> StartupChecks.verify(properties))
        .hasMessageNotContaining(ORGANIZER_SECRET);
  }

  @Test
  void limitsMustBePositive() {
    AppProperties properties =
        new AppProperties(
            "local",
            "from@example.org",
            "Konferenca",
            "a@example.org",
            "/config/options.json",
            "Soglašam.",
            "/data",
            new Recaptcha(true, "", "", "u"),
            organizer(false),
            "",
            new RateLimit(10, 0, 120),
            16384,
            false);

    assertThat(StartupChecks.violations(properties))
        .containsExactly("rate limits and MAX_REQUEST_BYTES must be positive");
  }
}

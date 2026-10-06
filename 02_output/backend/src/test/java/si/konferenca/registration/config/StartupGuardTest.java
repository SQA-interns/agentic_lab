package si.konferenca.registration.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class StartupGuardTest {

  private static AppProperties settings(
      String environment,
      boolean testMode,
      String siteKey,
      boolean httpsOnly,
      String cors,
      String configFile,
      String password) {
    return new AppProperties(
        environment,
        "Conference",
        configFile,
        "/tmp/copies",
        new AppProperties.Mail("smtp.example.com", 587, true, "", "", "from@example.com"),
        new AppProperties.Recaptcha(testMode, siteKey, siteKey, "https://verify.example.com"),
        new AppProperties.Organizer("organizer", password, "a@example.com", httpsOnly),
        cors,
        new AppProperties.RateLimit(20, 10, 120),
        16384);
  }

  private static AppProperties production() {
    return settings("production", false, "key", true, "", "/etc/conf.json", "0123456789abcdef");
  }

  @Test
  void acceptsAValidProductionConfiguration() {
    assertThatCode(() -> StartupGuard.check(production())).doesNotThrowAnyException();
  }

  @Test
  void refusesTestModeInProduction() {
    assertThatThrownBy(
            () ->
                StartupGuard.check(
                    settings("production", true, "key", true, "", "/c.json", "0123456789abcdef")))
        .isInstanceOf(InvalidConfigurationException.class)
        .hasMessageContaining("RECAPTCHA_TEST_MODE");
  }

  @Test
  void refusesEmptyRecaptchaKeysWhenTestModeIsOff() {
    assertThatThrownBy(
            () ->
                StartupGuard.check(settings("local", false, "", false, "", "", "0123456789abcdef")))
        .hasMessageContaining("RECAPTCHA_SITE_KEY");
  }

  @Test
  void allowsTestModeLocally() {
    assertThatCode(
            () -> StartupGuard.check(settings("local", true, "", false, "http://x", "", "short")))
        .doesNotThrowAnyException();
  }

  @Test
  void refusesPlainHttpOrganizerAccessInProduction() {
    assertThatThrownBy(
            () ->
                StartupGuard.check(
                    settings("production", false, "k", false, "", "/c.json", "0123456789abcdef")))
        .hasMessageContaining("ORGANIZER_HTTPS_ONLY");
  }

  @Test
  void refusesCorsOutsideLocal() {
    assertThatThrownBy(
            () ->
                StartupGuard.check(
                    settings("test", true, "", false, "http://localhost:5173", "", "p")))
        .hasMessageContaining("CORS_ALLOWED_ORIGIN");
  }

  @Test
  void refusesMissingConferenceFileAndShortPasswordInProduction() {
    assertThatThrownBy(
            () -> StartupGuard.check(settings("production", false, "k", true, "", "", "short")))
        .hasMessageContaining("CONFERENCE_CONFIG_FILE")
        .hasMessageContaining("at least 16 characters")
        .hasMessageNotContaining("short");
  }

  @Test
  void refusesUnknownEnvironmentAndMissingOrganizer() {
    AppProperties app =
        new AppProperties(
            "staging",
            "C",
            "",
            "/tmp",
            new AppProperties.Mail("h", 25, false, "", "", "f@example.com"),
            new AppProperties.Recaptcha(false, "k", "k", "u"),
            new AppProperties.Organizer("", "", " , ", true),
            "",
            new AppProperties.RateLimit(1, 1, 1),
            1);
    assertThatThrownBy(() -> StartupGuard.check(app))
        .hasMessageContaining("APP_ENVIRONMENT")
        .hasMessageContaining("ORGANIZER_USERNAME")
        .hasMessageContaining("ORGANIZER_EMAILS");
  }
}

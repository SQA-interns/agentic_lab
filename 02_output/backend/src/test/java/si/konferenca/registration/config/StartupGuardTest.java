package si.konferenca.registration.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class StartupGuardTest {

  private static final String PASSWORD = "0123456789abcdef";

  private static AppProperties properties(
      String environment,
      boolean testMode,
      String siteKey,
      String secretKey,
      String password,
      boolean httpsOnly,
      boolean smtpTls,
      String origins) {
    return new AppProperties(
        environment,
        "Conference",
        "/config.json",
        "/data",
        "no-reply@konferenca.si",
        smtpTls,
        origins,
        new AppProperties.Recaptcha(testMode, siteKey, secretKey, "https://verify"),
        new AppProperties.Organizer("organizer", password, "a@org.si, b@org.si", httpsOnly),
        new AppProperties.Limits(10, 10, 60, 16384));
  }

  private static AppProperties production() {
    return properties("production", false, "site", "secret", PASSWORD, true, true, "");
  }

  @Test
  void safeProductionAndLocalSettingsStart() {
    assertThatCode(() -> StartupGuard.check(production())).doesNotThrowAnyException();
    assertThatCode(
            () ->
                StartupGuard.check(
                    properties(
                        "local", true, "", "", PASSWORD, false, false, "http://localhost:5173")))
        .doesNotThrowAnyException();
  }

  @Test
  void productionRefusesTestModeAndEmptyKeys() {
    assertThatThrownBy(
            () ->
                StartupGuard.check(
                    properties("production", true, "site", "secret", PASSWORD, true, true, "")))
        .hasMessageContaining("RECAPTCHA_TEST_MODE must be false in production");
    assertThatThrownBy(
            () ->
                StartupGuard.check(
                    properties("production", false, " ", "secret", PASSWORD, true, true, "")))
        .hasMessageContaining("RECAPTCHA_SITE_KEY is required");
    assertThatThrownBy(
            () ->
                StartupGuard.check(
                    properties("production", false, "site", null, PASSWORD, true, true, "")))
        .hasMessageContaining("RECAPTCHA_SECRET_KEY is required");
  }

  @Test
  void liveModeNeedsKeysInEveryEnvironment() {
    assertThatThrownBy(
            () ->
                StartupGuard.check(properties("local", false, "", "", PASSWORD, false, false, "")))
        .hasMessageContaining("RECAPTCHA_SITE_KEY is required")
        .hasMessageContaining("RECAPTCHA_SECRET_KEY is required");
  }

  @Test
  void productionRefusesPlainHttpOrganizerAccessSmtpWithoutTlsAndCors() {
    assertThatThrownBy(
            () ->
                StartupGuard.check(
                    properties("production", false, "s", "k", PASSWORD, false, true, "")))
        .hasMessageContaining("ORGANIZER_HTTPS_ONLY must be true in production");
    assertThatThrownBy(
            () ->
                StartupGuard.check(
                    properties("production", false, "s", "k", PASSWORD, true, false, "")))
        .hasMessageContaining("SMTP_TLS must be true in production");
    assertThatThrownBy(
            () ->
                StartupGuard.check(
                    properties("production", false, "s", "k", PASSWORD, true, true, "https://x")))
        .hasMessageContaining("ALLOWED_ORIGINS must be empty in production");
  }

  @Test
  void unknownEnvironmentIsTreatedAsProduction() {
    assertThatThrownBy(
            () ->
                StartupGuard.check(properties("staging", true, "", "", PASSWORD, false, true, "")))
        .hasMessageContaining("APP_ENVIRONMENT must be production, local or test")
        .hasMessageContaining("RECAPTCHA_TEST_MODE must be false in production")
        .hasMessageContaining("ORGANIZER_HTTPS_ONLY must be true in production");
  }

  @Test
  void organizerPasswordNeedsSixteenCharacters() {
    assertThatThrownBy(
            () ->
                StartupGuard.check(
                    properties("test", true, "", "", "0123456789abcde", false, false, "")))
        .hasMessageContaining("ORGANIZER_PASSWORD must have at least 16 characters")
        .hasMessageNotContaining("0123456789abcde");
    assertThatThrownBy(
            () -> StartupGuard.check(properties("test", true, "", "", null, false, false, "")))
        .hasMessageContaining("ORGANIZER_PASSWORD");
  }

  @Test
  void requiredSettingsAreNamed() {
    AppProperties p =
        new AppProperties(
            "test",
            "C",
            " ",
            "",
            "not-an-email",
            false,
            "",
            new AppProperties.Recaptcha(true, "", "", ""),
            new AppProperties.Organizer(" ", PASSWORD, "a@org.si, nope", false),
            new AppProperties.Limits(0, 1, 1, 1023));

    assertThatThrownBy(() -> StartupGuard.check(p))
        .hasMessageContaining("ORGANIZER_USERNAME is required")
        .hasMessageContaining("ORGANIZER_EMAILS must list valid addresses")
        .hasMessageContaining("MAIL_FROM must be a valid address")
        .hasMessageContaining("CONFERENCE_CONFIG_FILE is required")
        .hasMessageContaining("JSON_COPY_DIR is required")
        .hasMessageContaining("rate limits");
  }

  @Test
  void organizerEmailsMayNotBeEmpty() {
    AppProperties p =
        new AppProperties(
            "test",
            "C",
            "/c.json",
            "/d",
            "a@b.si",
            false,
            null,
            new AppProperties.Recaptcha(true, "", "", ""),
            new AppProperties.Organizer("o", PASSWORD, " , ", false),
            new AppProperties.Limits(1, 1, 1, 1024));

    assertThatThrownBy(() -> StartupGuard.check(p)).hasMessageContaining("ORGANIZER_EMAILS");
  }

  @Test
  void listsAreSplitOnCommas() {
    org.assertj.core.api.Assertions.assertThat(AppProperties.split(" a , ,b "))
        .containsExactly("a", "b");
    org.assertj.core.api.Assertions.assertThat(AppProperties.split(null)).isEmpty();
  }
}

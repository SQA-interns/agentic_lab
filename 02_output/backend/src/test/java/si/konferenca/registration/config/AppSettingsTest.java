package si.konferenca.registration.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class AppSettingsTest {

  private static MockEnvironment valid() {
    return new MockEnvironment()
        .withProperty("APP_ENVIRONMENT", "production")
        .withProperty("ORGANIZER_EMAILS", "a@konferenca.si, b@konferenca.si")
        .withProperty("ORGANIZER_USERNAME", "organizer")
        .withProperty("ORGANIZER_PASSWORD", "0123456789abcdef") // gitleaks:allow (test-only value)
        .withProperty("CONFERENCE_CONFIG_PATH", "/config/conference.json")
        .withProperty("RECAPTCHA_SITE_KEY", "site")
        .withProperty("RECAPTCHA_SECRET_KEY", "secret");
  }

  @Test
  void es01_defaultsAreTheSafeValues() {
    AppSettings settings = AppSettings.from(valid().withProperty("APP_ENVIRONMENT", ""));

    assertThat(settings.production()).isTrue();
    assertThat(settings.organizerHttpsOnly()).isTrue();
    assertThat(settings.recaptchaTestMode()).isFalse();
    assertThat(settings.recaptchaVerifyUrl())
        .isEqualTo("https://www.google.com/recaptcha/api/siteverify");
    assertThat(settings.organizerEmails()).containsExactly("a@konferenca.si", "b@konferenca.si");
    assertThat(settings.registrationsPerMinute()).isEqualTo(10);
    assertThat(settings.maxRequestBytes()).isEqualTo(16384);
    assertThat(settings.corsAllowedOrigin()).isEmpty();
  }

  @Test
  void sr02_productionRefusesTestMode() {
    assertThatThrownBy(() -> AppSettings.from(valid().withProperty("RECAPTCHA_TEST_MODE", "true")))
        .hasMessageContaining("RECAPTCHA_TEST_MODE");
  }

  @Test
  void sr02_keysAreRequiredOutsideTestMode() {
    assertThatThrownBy(
            () ->
                AppSettings.from(
                    valid()
                        .withProperty("APP_ENVIRONMENT", "local")
                        .withProperty("RECAPTCHA_SECRET_KEY", "")))
        .hasMessageContaining("RECAPTCHA_SITE_KEY and RECAPTCHA_SECRET_KEY");
  }

  @Test
  void sr02_testModeNeedsNoKeysOutsideProduction() {
    AppSettings settings =
        AppSettings.from(
            valid()
                .withProperty("APP_ENVIRONMENT", "test")
                .withProperty("RECAPTCHA_TEST_MODE", "true")
                .withProperty("RECAPTCHA_SITE_KEY", "")
                .withProperty("RECAPTCHA_SECRET_KEY", ""));

    assertThat(settings.recaptchaTestMode()).isTrue();
  }

  @Test
  void sr06_productionRefusesPlainHttpOrganizerAccess() {
    assertThatThrownBy(
            () -> AppSettings.from(valid().withProperty("ORGANIZER_HTTPS_ONLY", "false")))
        .hasMessageContaining("ORGANIZER_HTTPS_ONLY");
    assertThat(
            AppSettings.from(
                    valid()
                        .withProperty("APP_ENVIRONMENT", "local")
                        .withProperty("ORGANIZER_HTTPS_ONLY", "false"))
                .organizerHttpsOnly())
        .isFalse();
  }

  @Test
  void sb03_shortOrganizerPasswordIsRefused() {
    assertThatThrownBy(() -> AppSettings.from(valid().withProperty("ORGANIZER_PASSWORD", "short")))
        .hasMessageContaining("ORGANIZER_PASSWORD");
  }

  @Test
  void es01_secretsHaveNoDefault() {
    MockEnvironment env = valid();
    env.setProperty("ORGANIZER_PASSWORD", "");

    assertThatThrownBy(() -> AppSettings.from(env)).hasMessageContaining("ORGANIZER_PASSWORD");
  }

  @Test
  void invalidOrganizerAddressIsRefused() {
    assertThatThrownBy(
            () ->
                AppSettings.from(valid().withProperty("ORGANIZER_EMAILS", "a@konferenca.si, nope")))
        .hasMessageContaining("ORGANIZER_EMAILS");
  }

  @Test
  void sr05_conferenceNameWithLineBreakIsRefused() {
    assertThatThrownBy(
            () -> AppSettings.from(valid().withProperty("CONFERENCE_NAME", "Konf\r\nBcc: x@y.si")))
        .hasMessageContaining("CONFERENCE_NAME");
  }

  @Test
  void unknownEnvironmentAndBadNumbersAreRefused() {
    assertThatThrownBy(() -> AppSettings.from(valid().withProperty("APP_ENVIRONMENT", "staging")))
        .hasMessageContaining("APP_ENVIRONMENT");
    assertThatThrownBy(() -> AppSettings.from(valid().withProperty("MAX_REQUEST_BYTES", "lots")))
        .hasMessageContaining("MAX_REQUEST_BYTES");
    assertThatThrownBy(
            () -> AppSettings.from(valid().withProperty("RATE_LIMIT_EXPORTS_PER_MINUTE", "0")))
        .hasMessageContaining("RATE_LIMIT_EXPORTS_PER_MINUTE");
  }

  @Test
  void es07_toStringShowsNoSecret() {
    assertThat(AppSettings.from(valid()).toString()).doesNotContain("0123456789abcdef", "secret");
  }
}

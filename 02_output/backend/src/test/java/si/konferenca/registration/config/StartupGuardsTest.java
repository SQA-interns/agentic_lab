package si.konferenca.registration.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class StartupGuardsTest {

  private static AppProperties properties(
      boolean testMode, String siteKey, boolean httpsOnly, String options, String emails, int max) {
    return new AppProperties(
        "Konf",
        "from@konf.si",
        new AppProperties.Smtp("smtp.konf.si", 587, true, "", ""),
        options,
        "/data",
        new AppProperties.Captcha(testMode, siteKey, siteKey, "https://verify"),
        new AppProperties.Organizer("org", "pw-long-enough", emails, httpsOnly),
        "",
        new AppProperties.RateLimit(10, 5, 10, 60),
        max);
  }

  private static MockEnvironment environment(String... profiles) {
    MockEnvironment environment = new MockEnvironment();
    environment.setActiveProfiles(profiles);
    environment.setProperty("spring.datasource.password", "db-password");
    return environment;
  }

  private static AppProperties productionReady() {
    return properties(false, "key", true, "/etc/options.json", "o@konf.si", 16384);
  }

  @Test
  void productionWithCompleteSettingsStarts() {
    new StartupGuards(productionReady(), environment()).afterPropertiesSet();
    new StartupGuards(productionReady(), environment("production")).afterPropertiesSet();
  }

  @Test
  void productionRefusesTestModePlainHttpAndBundledOptions() {
    AppProperties unsafe =
        properties(true, "", false, "classpath:contracts/x.json", "o@konf.si", 1);

    assertThat(new StartupGuards(unsafe, environment()).problems())
        .containsExactly(
            "RECAPTCHA_TEST_MODE is allowed only in the local and test environments",
            "ORGANIZER_HTTPS_ONLY may be false only on the local stack",
            "CONFERENCE_OPTIONS_FILE must be set in production");
    assertThatThrownBy(() -> new StartupGuards(unsafe, environment()).afterPropertiesSet())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageStartingWith("unsafe configuration: ");
  }

  @Test
  void localAndTestAllowTestModePlainHttpAndBundledOptions() {
    AppProperties local = properties(true, "", false, "classpath:x.json", "o@konf.si", 1);

    assertThat(new StartupGuards(local, environment("local")).problems()).isEmpty();
    assertThat(new StartupGuards(local, environment("test")).problems()).isEmpty();
  }

  @Test
  void keysAreRequiredOutsideTestMode() {
    AppProperties noKeys = properties(false, " ", true, "/o.json", "o@konf.si", 1);

    assertThat(new StartupGuards(noKeys, environment("local")).problems())
        .containsExactly("RECAPTCHA_SITE_KEY must be set", "RECAPTCHA_SECRET_KEY must be set");
  }

  @Test
  void secretsRecipientsAndLimitsAreChecked() {
    AppProperties bad =
        new AppProperties(
            "Konf",
            "from@konf.si",
            new AppProperties.Smtp("", 25, false, "", ""),
            "/o.json",
            "/data",
            new AppProperties.Captcha(true, "", "", "u"),
            new AppProperties.Organizer("", null, "not-an-address", true),
            "",
            new AppProperties.RateLimit(0, 5, 10, 60),
            16384);
    MockEnvironment environment = new MockEnvironment();
    environment.setActiveProfiles("test");

    assertThat(new StartupGuards(bad, environment).problems())
        .containsExactly(
            "ORGANIZER_USERNAME must be set",
            "ORGANIZER_PASSWORD must be set",
            "ORGANIZER_EMAILS must list one or more email addresses",
            "POSTGRES_PASSWORD must be set",
            "SMTP_HOST must be set",
            "MAX_REQUEST_BYTES and RATE_LIMIT_* must be positive");
    assertThat(
            new StartupGuards(properties(true, "", true, "/o", "", 0), environment("test"))
                .problems())
        .contains(
            "ORGANIZER_EMAILS must list one or more email addresses",
            "MAX_REQUEST_BYTES and RATE_LIMIT_* must be positive");
  }

  @Test
  void organizerEmailListIsSplitAndTrimmed() {
    assertThat(new AppProperties.Organizer("u", "p", " a@b.si , ,c@d.si", true).emailList())
        .containsExactly("a@b.si", "c@d.si");
    assertThat(new AppProperties.Organizer("u", "p", null, true).emailList()).isEmpty();
  }
}

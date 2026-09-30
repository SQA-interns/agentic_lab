package si.konferenca.registration.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class StartupChecksTest {

  static AppProperties props(
      String environment, boolean testMode, String site, String secret, boolean httpsOnly) {
    return props(environment, testMode, site, secret, httpsOnly, "organizer", "p".repeat(16));
  }

  static AppProperties props(
      String environment,
      boolean testMode,
      String site,
      String secret,
      boolean httpsOnly,
      String user,
      String password) {
    return new AppProperties(
        environment,
        "Conf",
        "/config/options.json",
        "/data",
        16384,
        new AppProperties.Mail("from@x.si", true, Duration.ofMinutes(5), 10),
        new AppProperties.Organizer(user, password, "o@x.si, p@x.si", httpsOnly),
        new AppProperties.Recaptcha(testMode, site, secret, "https://verify"),
        new AppProperties.Cors(""),
        new AppProperties.RateLimit(20, 10));
  }

  @Test
  void safeProductionAndLocalConfigurationsPass() {
    assertThat(StartupChecks.problems(props("production", false, "s", "k", true))).isEmpty();
    assertThat(StartupChecks.problems(props("local", true, "", "", false))).isEmpty();
  }

  @Test
  void productionRefusesTestMode() {
    assertThat(StartupChecks.problems(props("PRODUCTION", true, "s", "k", true)))
        .singleElement()
        .asString()
        .contains("RECAPTCHA_TEST_MODE");
  }

  @Test
  void keysAreRequiredOutsideTestMode() {
    assertThat(StartupChecks.problems(props("local", false, "", "k", false))).hasSize(1);
    assertThat(StartupChecks.problems(props("local", false, "s", " ", false))).hasSize(1);
    assertThat(StartupChecks.problems(props("local", false, null, null, false))).hasSize(1);
  }

  @Test
  void productionRequiresHttpsOnlyOrganizerAccess() {
    assertThat(StartupChecks.problems(props("production", false, "s", "k", false)))
        .singleElement()
        .asString()
        .contains("ORGANIZER_HTTPS_ONLY");
  }

  @Test
  void organizerCredentialsAreRequired() {
    assertThat(StartupChecks.problems(props("local", true, "", "", true, "", "p".repeat(16))))
        .singleElement()
        .asString()
        .contains("ORGANIZER_USERNAME");
    assertThat(StartupChecks.problems(props("local", true, "", "", true, "u", "p".repeat(15))))
        .singleElement()
        .asString()
        .contains("ORGANIZER_PASSWORD");
    assertThat(StartupChecks.problems(props("local", true, "", "", true, "u", null))).hasSize(1);
  }

  @Test
  void emailsOptionsFileAndAttemptsAreRequired() {
    AppProperties p =
        new AppProperties(
            "local",
            "Conf",
            " ",
            "/data",
            16384,
            new AppProperties.Mail("from@x.si", true, Duration.ofMinutes(5), 0),
            new AppProperties.Organizer("u", "p".repeat(16), " , ", true),
            new AppProperties.Recaptcha(true, "", "", "https://verify"),
            new AppProperties.Cors(""),
            new AppProperties.RateLimit(20, 10));

    assertThat(StartupChecks.problems(p))
        .hasSize(3)
        .anyMatch(s -> s.contains("ORGANIZER_EMAILS"))
        .anyMatch(s -> s.contains("CONFERENCE_OPTIONS_FILE"))
        .anyMatch(s -> s.contains("MAIL_MAX_ATTEMPTS"));
  }

  @Test
  void verifyThrowsWithoutRevealingValues() {
    AppProperties p = props("production", true, "site-value", "secret-value", true);

    assertThatThrownBy(() -> StartupChecks.verify(p))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageNotContaining("secret-value")
        .hasMessageNotContaining("p".repeat(16));
  }

  @Test
  void propertiesHelpers() {
    AppProperties p = props("local", true, "", "", true);

    assertThat(p.organizer().emailList()).containsExactly("o@x.si", "p@x.si");
    assertThat(p.production()).isFalse();
    assertThat(p.organizer().toString()).doesNotContain("p".repeat(16));
    assertThat(new AppProperties.Recaptcha(false, "s", "top-secret", "u").toString())
        .doesNotContain("top-secret");
    assertThat(new AppProperties.Organizer("u", "x", null, true).emailList()).isEmpty();
    assertThat(new AppProperties.Cors(" https://a.si , ,https://b.si").origins())
        .containsExactly("https://a.si", "https://b.si");
  }
}

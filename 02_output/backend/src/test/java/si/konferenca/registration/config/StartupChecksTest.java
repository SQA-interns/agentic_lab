package si.konferenca.registration.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import si.konferenca.registration.settings.AppProperties;

class StartupChecksTest {

  private static AppProperties props(
      boolean testMode,
      String siteKey,
      String secretKey,
      String user,
      String password,
      List<String> emails,
      boolean httpsOnly) {
    return new AppProperties(
        "K",
        new AppProperties.Mail("f@x.si"),
        "classpath:x",
        "d",
        new AppProperties.Recaptcha(testMode, siteKey, secretKey, "http://v"),
        new AppProperties.Organizer(user, password, emails, httpsOnly),
        new AppProperties.Cors(List.of()),
        new AppProperties.RateLimit(1, 1, 1),
        10);
  }

  private static AppProperties valid() {
    return props(false, "site", "secret", "org", "password", List.of("o@x.si"), true);
  }

  private static List<String> problems(AppProperties p, String... profiles) {
    MockEnvironment env = new MockEnvironment();
    env.setActiveProfiles(profiles);
    return new StartupChecks(p, env).problems();
  }

  @Test
  void validConfigurationHasNoProblems() {
    assertThat(problems(valid())).isEmpty();
    assertThat(problems(valid(), "production")).isEmpty();
  }

  @Test
  void keysAreRequiredOutsideTestMode() {
    assertThat(problems(props(false, "", "s", "o", "p", List.of("o@x.si"), true))).hasSize(1);
    assertThat(problems(props(false, "k", " ", "o", "p", List.of("o@x.si"), true))).hasSize(1);
    assertThat(problems(props(false, null, null, "o", "p", List.of("o@x.si"), true)))
        .containsExactly(
            "RECAPTCHA_SITE_KEY and RECAPTCHA_SECRET_KEY are required when test mode is off");
    assertThat(problems(props(true, "", "", "o", "p", List.of("o@x.si"), false))).isEmpty();
  }

  @Test
  void productionForbidsTestModeAndPlainHttpOrganizerAccess() {
    assertThat(problems(props(true, "k", "s", "o", "p", List.of("o@x.si"), true), "production"))
        .containsExactly("RECAPTCHA_TEST_MODE must be off in production");
    assertThat(problems(props(false, "k", "s", "o", "p", List.of("o@x.si"), false), "production"))
        .containsExactly("ORGANIZER_HTTPS_ONLY must be on in production");
  }

  @Test
  void organizerAccountAndAddressesAreRequired() {
    assertThat(problems(props(false, "k", "s", "", "p", List.of("o@x.si"), true))).hasSize(1);
    assertThat(problems(props(false, "k", "s", "o", null, List.of("o@x.si"), true))).hasSize(1);
    assertThat(problems(props(false, "k", "s", "o", "p", List.of(), true)))
        .containsExactly("ORGANIZER_EMAILS must list at least one valid address");
    assertThat(problems(props(false, "k", "s", "o", "p", List.of("o@x.si", "bad"), true)))
        .hasSize(1);
    assertThat(problems(props(false, "k", "s", "o", "p", List.of(" o@x.si "), true))).isEmpty();
  }

  @Test
  void refusesToStartWithAllProblemsNamedButNoValues() {
    StartupChecks checks =
        new StartupChecks(
            props(true, "", "", "", "hunter2-secret", List.of(), false), productionEnv());

    assertThatThrownBy(checks::afterPropertiesSet)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageStartingWith("Refusing to start: ")
        .hasMessageContaining("RECAPTCHA_TEST_MODE")
        .hasMessageContaining("ORGANIZER_HTTPS_ONLY")
        .hasMessageContaining("ORGANIZER_USERNAME")
        .hasMessageContaining("ORGANIZER_EMAILS")
        .hasMessageNotContaining("hunter2");
  }

  @Test
  void settingsNeverPrintSecrets() {
    AppProperties p = valid();

    assertThat(p.recaptcha().toString()).doesNotContain("secret");
    assertThat(p.organizer().toString()).doesNotContain("password");
  }

  private static MockEnvironment productionEnv() {
    MockEnvironment env = new MockEnvironment();
    env.setActiveProfiles("production");
    return env;
  }
}

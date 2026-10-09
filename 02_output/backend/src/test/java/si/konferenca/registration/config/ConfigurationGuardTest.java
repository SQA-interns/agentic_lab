package si.konferenca.registration.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/** Startup refusals (SR-02, SB-03, ES-01). */
class ConfigurationGuardTest {

  private static AppProperties properties(
      boolean testMode,
      String siteKey,
      String secret,
      String user,
      String password,
      List<String> emails,
      String optionsFile,
      String mailFrom) {
    return new AppProperties(
        "Conf",
        mailFrom,
        optionsFile,
        Path.of("copies"),
        new AppProperties.Recaptcha(testMode, siteKey, secret, URI.create("http://x")),
        new AppProperties.Organizer(user, password, emails, true),
        new AppProperties.Smtp("h", 25, false, "", ""),
        null,
        new AppProperties.Limits(1, 1, 1, 1));
  }

  private static AppProperties valid() {
    return properties(
        false,
        "site",
        "secret",
        "org",
        "pw",
        List.of("o@example.org"),
        "file:/etc/options.json",
        "from@example.org");
  }

  private static MockEnvironment production() {
    MockEnvironment environment = new MockEnvironment();
    environment.setActiveProfiles(ConfigurationGuard.PRODUCTION_PROFILE);
    return environment;
  }

  @Test
  void completeProductionConfigurationStarts() {
    assertThatCode(() -> ConfigurationGuard.check(valid(), production()))
        .doesNotThrowAnyException();
  }

  @Test
  void testModeIsRefusedInProduction() {
    AppProperties p =
        properties(
            true, "", "", "org", "pw", List.of("o@example.org"), "file:/o.json", "f@example.org");

    assertThatThrownBy(() -> ConfigurationGuard.check(p, production()))
        .hasMessageContaining("RECAPTCHA_TEST_MODE");
    assertThatCode(() -> ConfigurationGuard.check(p, new MockEnvironment()))
        .as("test mode outside production")
        .doesNotThrowAnyException();
  }

  @Test
  void missingRecaptchaKeysAreRefusedWithoutTestMode() {
    for (String[] keys : new String[][] {{"", "secret"}, {"site", " "}, {null, null}}) {
      AppProperties p =
          properties(
              false,
              keys[0],
              keys[1],
              "org",
              "pw",
              List.of("o@example.org"),
              "classpath:o.json",
              "f@example.org");
      assertThatThrownBy(() -> ConfigurationGuard.check(p, new MockEnvironment()))
          .hasMessageContaining("RECAPTCHA_SITE_KEY")
          .hasMessageNotContaining("secret");
    }
  }

  @Test
  void missingOrganizerCredentialsAreRefused() {
    AppProperties noUser =
        properties(
            true, "", "", " ", "pw", List.of("o@example.org"), "classpath:o", "f@example.org");
    AppProperties noPassword =
        properties(
            true, "", "", "org", null, List.of("o@example.org"), "classpath:o", "f@example.org");

    assertThatThrownBy(() -> ConfigurationGuard.check(noUser, new MockEnvironment()))
        .hasMessageContaining("ORGANIZER_USERNAME");
    assertThatThrownBy(() -> ConfigurationGuard.check(noPassword, new MockEnvironment()))
        .hasMessageContaining("ORGANIZER_PASSWORD");
  }

  @Test
  void organizerEmailsMustBeValidAndPresent() {
    AppProperties none =
        properties(true, "", "", "org", "pw", List.of(), "classpath:o", "f@example.org");
    AppProperties invalid =
        properties(
            true,
            "",
            "",
            "org",
            "pw",
            List.of("o@example.org", "nope"),
            "classpath:o",
            "f@example.org");

    assertThatThrownBy(() -> ConfigurationGuard.check(none, new MockEnvironment()))
        .hasMessageContaining("ORGANIZER_EMAILS");
    assertThatThrownBy(() -> ConfigurationGuard.check(invalid, new MockEnvironment()))
        .hasMessageContaining("ORGANIZER_EMAILS");
  }

  @Test
  void productionNeedsAnExplicitOptionsFile() {
    AppProperties bundled =
        properties(
            false,
            "s",
            "k",
            "org",
            "pw",
            List.of("o@example.org"),
            "classpath:conference-options.json",
            "f@example.org");

    assertThatThrownBy(() -> ConfigurationGuard.check(bundled, production()))
        .hasMessageContaining("OPTIONS_FILE");
    assertThatCode(() -> ConfigurationGuard.check(bundled, new MockEnvironment()))
        .doesNotThrowAnyException();
  }

  @Test
  void senderAddressMustBeValid() {
    AppProperties p =
        properties(
            true, "", "", "org", "pw", List.of("o@example.org"), "classpath:o", "not an address");

    assertThatThrownBy(() -> ConfigurationGuard.check(p, new MockEnvironment()))
        .hasMessageContaining("MAIL_FROM");
  }

  @Test
  void secretsDoNotAppearInToString() {
    AppProperties p = valid();

    org.assertj.core.api.Assertions.assertThat(p.toString())
        .doesNotContain("secret")
        .doesNotContain("pw");
  }
}

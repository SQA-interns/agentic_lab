package si.konferenca.registration.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class StartupChecksTest {

  public static AppProperties props(
      String environment,
      boolean testMode,
      String siteKey,
      String secretKey,
      boolean httpsOnly,
      String cors,
      String optionsFile,
      String password) {
    return new AppProperties(
        environment,
        "Conf",
        optionsFile,
        "/tmp/copies",
        "from@example.si",
        new AppProperties.Recaptcha(testMode, siteKey, secretKey, "https://verify"),
        new AppProperties.Organizer("org", password, "a@example.si, b@example.si", httpsOnly),
        cors,
        new AppProperties.RateLimit(10, 10),
        16384,
        new AppProperties.Retention(365, "0 0 3 * * *"));
  }

  public static AppProperties validProduction() {
    return props(
        "production", false, "site", "secret", true, "", "/etc/options.json", "x".repeat(16));
  }

  @Test
  void validProductionConfigurationPasses() {
    assertThat(StartupChecks.problems(validProduction(), "smtp.example.si")).isEmpty();
  }

  @Test
  @DisplayName("SR-02 production refuses test mode and empty keys")
  void productionRefusesTestModeAndEmptyKeys() {
    AppProperties p = props("production", true, "", "", true, "", "/o.json", "x".repeat(16));
    assertThat(StartupChecks.problems(p, "smtp"))
        .contains(
            "RECAPTCHA_TEST_MODE must be off in production",
            "RECAPTCHA_SITE_KEY and RECAPTCHA_SECRET_KEY are required in production");
  }

  @Test
  @DisplayName("SR-06 production refuses plain-HTTP organizer access and CORS")
  void productionRefusesInsecureOrganizerAccessAndCors() {
    AppProperties p =
        props(
            "production",
            false,
            "s",
            "k",
            false,
            "http://localhost:5173",
            "/o.json",
            "x".repeat(16));
    assertThat(StartupChecks.problems(p, "smtp"))
        .contains(
            "ORGANIZER_HTTPS_ONLY must be on in production",
            "CORS_ALLOWED_ORIGIN must be empty in production");
  }

  @Test
  void productionRequiresOptionsFileAndSmtpHost() {
    AppProperties p = props("production", false, "s", "k", true, "", "", "x".repeat(16));
    assertThat(StartupChecks.problems(p, ""))
        .contains(
            "CONFERENCE_OPTIONS_FILE is required in production",
            "SMTP_HOST is required in production");
  }

  @Test
  @DisplayName("an unknown environment is treated as production (fails safe)")
  void unknownEnvironmentIsProduction() {
    AppProperties p = props("staging", true, "", "", false, "", "", "x".repeat(16));
    assertThat(p.production()).isTrue();
    assertThat(StartupChecks.problems(p, "smtp")).hasSizeGreaterThanOrEqualTo(4);
  }

  @Test
  void localAndTestAllowTestModeWithoutKeys() {
    for (String env : new String[] {"local", "test"}) {
      AppProperties p =
          props(env, true, "", "", false, "http://localhost:5173", "", "x".repeat(16));
      assertThat(p.production()).isFalse();
      assertThat(StartupChecks.problems(p, "")).isEmpty();
    }
  }

  @Test
  @DisplayName("SB-03 organizer credentials are required in every environment")
  void organizerCredentialsAreAlwaysRequired() {
    AppProperties p =
        new AppProperties(
            "local",
            "Conf",
            "",
            "/tmp",
            "f@x.si",
            new AppProperties.Recaptcha(true, "", "", "u"),
            new AppProperties.Organizer(" ", "short", " , ", false),
            "",
            new AppProperties.RateLimit(0, 10),
            0,
            new AppProperties.Retention(0, "c"));
    assertThat(StartupChecks.problems(p, ""))
        .containsExactlyInAnyOrder(
            "ORGANIZER_USERNAME is empty",
            "ORGANIZER_PASSWORD must have at least 16 characters",
            "ORGANIZER_EMAILS is empty",
            "MAX_REQUEST_BYTES must be positive",
            "rate limits must be positive",
            "RETENTION_DAYS must be positive");
  }

  @Test
  void passwordOfExactlySixteenCharactersIsAccepted() {
    AppProperties p = props("local", true, "", "", false, "", "", "x".repeat(16));
    assertThat(StartupChecks.problems(p, "")).isEmpty();
    AppProperties shorter = props("local", true, "", "", false, "", "", "x".repeat(15));
    assertThat(StartupChecks.problems(shorter, "")).hasSize(1);
  }

  @Test
  void organizerEmailListIsTrimmedAndSplit() {
    assertThat(validProduction().organizer().emailList())
        .containsExactly("a@example.si", "b@example.si");
    assertThat(new AppProperties.Organizer("u", "p", null, true).emailList()).isEmpty();
  }

  @Test
  void validatorThrowsWithAllProblemsButNoValues() {
    AppProperties p = props("production", true, "", "", false, "", "", "secret-value-123");
    StartupValidator validator = new StartupValidator(p, "");
    assertThatThrownBy(validator::afterPropertiesSet)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("RECAPTCHA_TEST_MODE")
        .hasMessageNotContaining("secret-value-123");
  }
}

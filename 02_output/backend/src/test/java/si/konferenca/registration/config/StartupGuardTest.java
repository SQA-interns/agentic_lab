package si.konferenca.registration.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class StartupGuardTest {

  private static AppProperties props(
      boolean testMode,
      String siteKey,
      boolean httpsOnly,
      String password,
      boolean starttls,
      String cors) {
    return new AppProperties(
        "K",
        "f@x.si",
        List.of(" o@x.si ", ""),
        "/tmp/x",
        "classpath:o.yaml",
        cors,
        16384,
        new AppProperties.Smtp("smtp", 587, starttls, "", ""),
        new AppProperties.Recaptcha(testMode, siteKey, siteKey, "https://v"),
        new AppProperties.Organizer("organizer", password, httpsOnly),
        new AppProperties.RateLimit(1, 1, 1));
  }

  private static final AppProperties SAFE = props(false, "key", true, "0123456789abcdef", true, "");

  @Test
  void organizerEmailsAreTrimmedAndEmptiesDropped() {
    assertThat(SAFE.organizerEmails()).containsExactly("o@x.si");
  }

  @Test
  void safeProductionSettingsStart() {
    assertThatCode(() -> new StartupGuard(new MockEnvironment(), SAFE).afterPropertiesSet())
        .doesNotThrowAnyException();
  }

  @Test
  void productionRefusesEveryUnsafeSetting() {
    AppProperties unsafe = props(true, "", false, "short", false, "http://x");

    assertThat(StartupGuard.productionProblems(unsafe))
        .containsExactly(
            "reCAPTCHA test mode must be off",
            "reCAPTCHA site and secret keys must be set",
            "organizer HTTPS-only access must be on",
            "organizer password must have at least 16 characters",
            "SMTP STARTTLS must be on",
            "CORS must not be enabled");
    assertThatThrownBy(() -> new StartupGuard(new MockEnvironment(), unsafe).afterPropertiesSet())
        .hasMessageStartingWith("Refusing to start");
  }

  @Test
  void localAndTestProfilesAllowTestSettings() {
    AppProperties local = props(true, "", false, "short", false, "http://x");
    MockEnvironment env = new MockEnvironment();
    env.setActiveProfiles("local");

    assertThatCode(() -> new StartupGuard(env, local).afterPropertiesSet())
        .doesNotThrowAnyException();
  }

  @Test
  void organizerCredentialsAreAlwaysRequired() {
    AppProperties noUser =
        new AppProperties(
            "K",
            "f",
            List.of("o@x.si"),
            "/tmp",
            "c",
            "",
            1,
            SAFE.smtp(),
            SAFE.recaptcha(),
            new AppProperties.Organizer(" ", "", true),
            SAFE.rateLimit());
    MockEnvironment env = new MockEnvironment();
    env.setActiveProfiles("test");

    assertThatThrownBy(() -> new StartupGuard(env, noUser).afterPropertiesSet())
        .hasMessageContaining("organizer username and password");
  }

  @Test
  void productionNeedsOrganizerRecipients() {
    AppProperties none =
        new AppProperties(
            "K",
            "f",
            null,
            "/tmp",
            "c",
            "",
            1,
            SAFE.smtp(),
            SAFE.recaptcha(),
            SAFE.organizer(),
            SAFE.rateLimit());

    assertThat(StartupGuard.productionProblems(none))
        .containsExactly("organizer notification recipients must be set");
  }
}

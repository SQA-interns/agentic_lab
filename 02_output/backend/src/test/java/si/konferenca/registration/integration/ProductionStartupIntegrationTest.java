package si.konferenca.registration.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import si.konferenca.registration.acceptance.support.AcceptanceEnvironment;
import si.konferenca.registration.acceptance.support.RunningApp;

/** SR-02: without the local/test profile the backend refuses test-only settings. */
class ProductionStartupIntegrationTest {

  private static Map<String, String> production() {
    Map<String, String> c = AcceptanceEnvironment.defaultConfiguration();
    c.put("spring.profiles.active", "");
    c.put("SMTP_STARTTLS", "true");
    c.put("ORGANIZER_PASSWORD", "a-long-production-password");
    c.put("RECAPTCHA_SITE_KEY", "site");
    c.put("RECAPTCHA_SECRET_KEY", "secret");
    c.put("RECAPTCHA_TEST_MODE", "false");
    return c;
  }

  @Test
  void productionRefusesRecaptchaTestMode() {
    Map<String, String> c = production();
    c.put("RECAPTCHA_TEST_MODE", "true");

    assertThat(RunningApp.startupFailure(c))
        .hasStackTraceContaining("reCAPTCHA test mode must be off");
  }

  @Test
  void productionRefusesEmptyRecaptchaKeys() {
    Map<String, String> c = production();
    c.put("RECAPTCHA_SECRET_KEY", "");

    assertThat(RunningApp.startupFailure(c)).hasStackTraceContaining("keys must be set");
  }

  @Test
  void productionRefusesMissingOptionsFile() {
    Map<String, String> c = production();
    c.remove("OPTIONS_FILE");

    assertThat(RunningApp.startupFailure(c)).hasStackTraceContaining("OPTIONS_FILE");
  }

  @Test
  void safeProductionSettingsStart() {
    assertThat(RunningApp.startupFailure(production())).isNull();
  }
}

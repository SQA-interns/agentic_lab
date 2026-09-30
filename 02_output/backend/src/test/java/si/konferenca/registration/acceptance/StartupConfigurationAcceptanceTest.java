package si.konferenca.registration.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import si.konferenca.registration.acceptance.support.Api;
import si.konferenca.registration.acceptance.support.RecaptchaMock;
import si.konferenca.registration.acceptance.support.Startup;
import si.konferenca.registration.acceptance.support.TestEnvironment;

/** US-001 / SR-02: reCAPTCHA test mode and keys are enforced at startup. */
class StartupConfigurationAcceptanceTest {

  private static Map<String, String> productionMode() {
    Map<String, String> p = TestEnvironment.baseProperties();
    p.put("app.recaptcha.test-mode", "false");
    p.put("app.recaptcha.site-key", RecaptchaMock.SITE_KEY);
    p.put("app.recaptcha.secret-key", RecaptchaMock.SECRET);
    return p;
  }

  @Test
  @DisplayName("AC-001-15 control: test mode off with both keys starts")
  void ac001_15_startsWithKeysAndTestModeOff() {
    assertThat(Startup.starts(productionMode())).isTrue();
  }

  @Test
  @DisplayName("AC-001-15 test mode off with an empty secret key refuses to start")
  void ac001_15_refusesEmptySecretKey() {
    Map<String, String> p = productionMode();
    p.put("app.recaptcha.secret-key", "");

    assertThat(Startup.starts(p)).isFalse();
  }

  @Test
  @DisplayName("AC-001-15 test mode off with an empty site key refuses to start")
  void ac001_15_refusesEmptySiteKey() {
    Map<String, String> p = productionMode();
    p.put("app.recaptcha.site-key", "");

    assertThat(Startup.starts(p)).isFalse();
  }

  @Test
  @DisplayName("AC-001-15 without a test-mode setting, empty keys refuse to start")
  void ac001_15_defaultIsNotTestMode() {
    Map<String, String> p = TestEnvironment.baseProperties();
    p.remove("app.recaptcha.test-mode");

    assertThat(Startup.starts(p)).isFalse();
  }

  @Test
  @DisplayName("AC-001-15 without a test-mode setting, the client configuration says test mode off")
  void ac001_15_defaultReportsTestModeOff() {
    Map<String, String> p = productionMode();
    p.remove("app.recaptcha.test-mode");

    Startup.Outcome outcome = Startup.tryStart(p);
    assertThat(outcome.started()).as("start failure: %s", outcome.failure()).isTrue();
    try {
      Api.Response config = new Api(outcome.port()).get("/api/config");
      assertThat(config.status()).isEqualTo(200);
      assertThat(config.json().get("recaptchaTestMode").asBoolean()).isFalse();
    } finally {
      outcome.context().close();
    }
  }

  @Test
  @DisplayName("AC-001-15 control: the production profile with test mode off starts")
  void ac001_15_productionStartsWhenConfigured() {
    Map<String, String> p = productionMode();
    p.put("spring.profiles.active", "production");
    p.put("app.organizer.https-only", "true");

    assertThat(Startup.starts(p)).isTrue();
  }

  @Test
  @DisplayName("AC-001-15 the production profile with test mode on refuses to start")
  void ac001_15_productionRefusesTestMode() {
    Map<String, String> p = productionMode();
    p.put("spring.profiles.active", "production");
    p.put("app.organizer.https-only", "true");
    p.put("app.recaptcha.test-mode", "true");

    assertThat(Startup.starts(p)).isFalse();
  }

  @Test
  @DisplayName("AC-001-15 the production profile with empty keys refuses to start")
  void ac001_15_productionRefusesEmptyKeys() {
    Map<String, String> p = productionMode();
    p.put("spring.profiles.active", "production");
    p.put("app.organizer.https-only", "true");
    p.put("app.recaptcha.site-key", "");
    p.put("app.recaptcha.secret-key", "");

    assertThat(Startup.starts(p)).isFalse();
  }
}

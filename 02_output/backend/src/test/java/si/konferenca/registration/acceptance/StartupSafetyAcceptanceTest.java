package si.konferenca.registration.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import si.konferenca.registration.RegistrationApplication;
import si.konferenca.registration.acceptance.support.Api;
import si.konferenca.registration.acceptance.support.TestInfrastructure;

/** SR-02, SR-06, ES-01: unsafe production configuration refuses to start. */
class StartupSafetyAcceptanceTest {

  private static Map<String, Object> production() {
    Map<String, Object> p = new HashMap<>(TestInfrastructure.defaultProperties());
    p.put("server.port", "0");
    p.put("app.json-copy-dir", TestInfrastructure.newTempDir("copies").toString());
    p.put("app.environment", "production");
    p.put("app.recaptcha.test-mode", "false");
    p.put("app.recaptcha.site-key", "prod-site-key");
    p.put("app.recaptcha.secret-key", "prod-secret-key");
    p.put("app.organizer.https-only", "true");
    return p;
  }

  private static ConfigurableApplicationContext start(Map<String, Object> props) {
    return new SpringApplicationBuilder(RegistrationApplication.class).properties(props).run();
  }

  @Test
  @DisplayName("SR-02 a safe production configuration starts with test mode off")
  void sr02SafeProductionConfigurationStarts() {
    try (ConfigurableApplicationContext app = start(production())) {
      int port = Integer.parseInt(app.getEnvironment().getProperty("local.server.port"));
      Api.Response r = new Api(port).get("/api/config");
      assertThat(r.status()).as(r.text()).isEqualTo(200);
      assertThat(r.json().path("captchaTestMode").asBoolean()).isFalse();
      assertThat(r.json().path("recaptchaSiteKey").asString()).isEqualTo("prod-site-key");
    }
  }

  @Test
  @DisplayName("SR-02 production refuses to start with reCAPTCHA test mode on")
  void sr02ProductionRefusesTestMode() {
    Map<String, Object> props = production();
    props.put("app.recaptcha.test-mode", "true");

    assertThatThrownBy(() -> start(props).close()).isInstanceOf(Exception.class);
  }

  @Test
  @DisplayName("SR-02 production refuses to start with empty reCAPTCHA keys")
  void sr02ProductionRefusesEmptyKeys() {
    Map<String, Object> noSecret = production();
    noSecret.put("app.recaptcha.secret-key", "");
    Map<String, Object> noSiteKey = production();
    noSiteKey.put("app.recaptcha.site-key", "");

    assertThatThrownBy(() -> start(noSecret).close()).isInstanceOf(Exception.class);
    assertThatThrownBy(() -> start(noSiteKey).close()).isInstanceOf(Exception.class);
  }

  @Test
  @DisplayName("SR-02 test mode is off by default: without keys the application refuses to start")
  void sr02TestModeIsOffByDefault() {
    Map<String, Object> props = production();
    props.remove("app.recaptcha.test-mode");
    props.put("app.environment", "test");
    props.put("app.recaptcha.site-key", "");
    props.put("app.recaptcha.secret-key", "");

    assertThatThrownBy(() -> start(props).close()).isInstanceOf(Exception.class);
  }

  @Test
  @DisplayName("SR-06 production refuses to start with organizer HTTPS-only access disabled")
  void sr06ProductionRefusesPlainHttpOrganizerAccess() {
    Map<String, Object> props = production();
    props.put("app.organizer.https-only", "false");

    assertThatThrownBy(() -> start(props).close()).isInstanceOf(Exception.class);
  }

  @Test
  @DisplayName("SR-02 ES-01 the application refuses to start without organizer credentials")
  void es01MissingOrganizerPasswordRefusesStart() {
    Map<String, Object> props = production();
    props.put("app.organizer.password", "");

    assertThatThrownBy(() -> start(props).close()).isInstanceOf(Exception.class);
  }
}

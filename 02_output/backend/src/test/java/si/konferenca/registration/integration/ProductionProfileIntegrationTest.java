package si.konferenca.registration.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import si.konferenca.registration.RegistrationApplication;
import si.konferenca.registration.acceptance.support.RecaptchaMock;
import si.konferenca.registration.acceptance.support.TestEnvironment;

/** The production profile defaults to SMTP with STARTTLS required (SB-04, F-09). */
class ProductionProfileIntegrationTest {

  @Test
  void productionRequiresStartTlsByDefault() {
    Map<String, String> p = TestEnvironment.baseProperties();
    p.put("app.recaptcha.test-mode", "false");
    p.put("app.recaptcha.site-key", RecaptchaMock.SITE_KEY);
    p.put("app.recaptcha.secret-key", RecaptchaMock.SECRET);
    p.put("app.organizer.https-only", "true");
    p.put("spring.profiles.active", "production");
    List<String> args = new ArrayList<>(List.of("--server.port=0"));
    p.forEach((k, v) -> args.add("--" + k + "=" + v));

    try (ConfigurableApplicationContext ctx =
        new SpringApplicationBuilder(RegistrationApplication.class)
            .run(args.toArray(String[]::new))) {
      assertThat(
              ctx.getEnvironment().getProperty("spring.mail.properties.mail.smtp.starttls.enable"))
          .isEqualTo("true");
      assertThat(
              ctx.getEnvironment()
                  .getProperty("spring.mail.properties.mail.smtp.starttls.required"))
          .isEqualTo("true");
    }
  }
}

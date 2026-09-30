package si.konferenca.registration.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;
import si.konferenca.registration.domain.EmailAddresses;
import si.konferenca.registration.settings.AppProperties;

/**
 * Refuses to start with an unsafe or incomplete configuration (SR-02, AC-001-15;
 * 02_contracts/configuration.md, "Startup refusal"). Messages name settings, never values.
 */
@Component
public class StartupChecks implements InitializingBean {

  private final AppProperties properties;
  private final Environment environment;

  public StartupChecks(AppProperties properties, Environment environment) {
    this.properties = properties;
    this.environment = environment;
  }

  @Override
  public void afterPropertiesSet() {
    List<String> problems = problems();
    if (!problems.isEmpty()) {
      throw new IllegalStateException("Refusing to start: " + String.join("; ", problems));
    }
  }

  List<String> problems() {
    List<String> problems = new ArrayList<>();
    AppProperties.Recaptcha recaptcha = properties.recaptcha();
    if (!recaptcha.testMode() && (isBlank(recaptcha.siteKey()) || isBlank(recaptcha.secretKey()))) {
      problems.add(
          "RECAPTCHA_SITE_KEY and RECAPTCHA_SECRET_KEY are required when test mode is off");
    }
    if (environment.acceptsProfiles(Profiles.of("production"))) {
      if (recaptcha.testMode()) {
        problems.add("RECAPTCHA_TEST_MODE must be off in production");
      }
      if (!properties.organizer().httpsOnly()) {
        problems.add("ORGANIZER_HTTPS_ONLY must be on in production");
      }
    }
    AppProperties.Organizer organizer = properties.organizer();
    if (isBlank(organizer.username()) || isBlank(organizer.password())) {
      problems.add("ORGANIZER_USERNAME and ORGANIZER_PASSWORD are required");
    }
    List<String> emails = organizer.emails().stream().map(String::strip).toList();
    if (emails.isEmpty() || !emails.stream().allMatch(EmailAddresses::isValid)) {
      problems.add("ORGANIZER_EMAILS must list at least one valid address");
    }
    return problems;
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}

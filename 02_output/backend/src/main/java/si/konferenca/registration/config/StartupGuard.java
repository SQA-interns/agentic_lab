package si.konferenca.registration.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * Refuses to start a production instance (neither 'local' nor 'test' profile) with test-only or
 * unsafe settings (SR-02, SR-06, SB-04, spec section 5).
 */
@Component
public class StartupGuard implements InitializingBean {

  static final int MIN_PASSWORD_LENGTH = 16;

  private final Environment environment;
  private final AppProperties properties;

  public StartupGuard(Environment environment, AppProperties properties) {
    this.environment = environment;
    this.properties = properties;
  }

  @Override
  public void afterPropertiesSet() {
    List<String> problems = new ArrayList<>();
    if (properties.organizer() == null
        || isBlank(properties.organizer().username())
        || isBlank(properties.organizer().password())) {
      problems.add("organizer username and password must be set");
    }
    if (!environment.acceptsProfiles(Profiles.of("local", "test"))) {
      problems.addAll(productionProblems(properties));
    }
    if (!problems.isEmpty()) {
      throw new IllegalStateException("Refusing to start: " + String.join("; ", problems));
    }
  }

  static List<String> productionProblems(AppProperties p) {
    List<String> problems = new ArrayList<>();
    if (p.recaptcha().testMode()) {
      problems.add("reCAPTCHA test mode must be off");
    }
    if (isBlank(p.recaptcha().siteKey()) || isBlank(p.recaptcha().secretKey())) {
      problems.add("reCAPTCHA site and secret keys must be set");
    }
    if (!p.organizer().httpsOnly()) {
      problems.add("organizer HTTPS-only access must be on");
    }
    if (p.organizer().password() == null
        || p.organizer().password().length() < MIN_PASSWORD_LENGTH) {
      problems.add("organizer password must have at least " + MIN_PASSWORD_LENGTH + " characters");
    }
    if (!p.smtp().starttls()) {
      problems.add("SMTP STARTTLS must be on");
    }
    if (!isBlank(p.corsAllowedOrigin())) {
      problems.add("CORS must not be enabled");
    }
    if (p.organizerEmails().isEmpty()) {
      problems.add("organizer notification recipients must be set");
    }
    return problems;
  }

  private static boolean isBlank(String s) {
    return s == null || s.isBlank();
  }
}

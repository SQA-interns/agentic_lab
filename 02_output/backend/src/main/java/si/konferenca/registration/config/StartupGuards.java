package si.konferenca.registration.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * Refuses to start with an unsafe or incomplete configuration (specification section 5, SR-02).
 * Messages name the setting, never its value.
 */
@Component
public class StartupGuards implements InitializingBean {

  private final AppProperties properties;
  private final Environment environment;

  public StartupGuards(AppProperties properties, Environment environment) {
    this.properties = properties;
    this.environment = environment;
  }

  @Override
  public void afterPropertiesSet() {
    List<String> problems = problems();
    if (!problems.isEmpty()) {
      throw new IllegalStateException("unsafe configuration: " + String.join("; ", problems));
    }
  }

  List<String> problems() {
    List<String> problems = new ArrayList<>();
    boolean production =
        !environment.acceptsProfiles(Profiles.of("local"))
            && !environment.acceptsProfiles(Profiles.of("test"));
    AppProperties.Organizer organizer = properties.organizer();
    require(problems, organizer.username(), "ORGANIZER_USERNAME");
    require(problems, organizer.password(), "ORGANIZER_PASSWORD");
    if (organizer.emailList().isEmpty()
        || organizer.emailList().stream().anyMatch(e -> !e.contains("@"))) {
      problems.add("ORGANIZER_EMAILS must list one or more email addresses");
    }
    require(problems, environment.getProperty("spring.datasource.password"), "POSTGRES_PASSWORD");
    require(problems, properties.smtp().host(), "SMTP_HOST");
    AppProperties.Captcha captcha = properties.captcha();
    if (captcha.testMode() && production) {
      problems.add("RECAPTCHA_TEST_MODE is allowed only in the local and test environments");
    }
    if (!captcha.testMode()) {
      require(problems, captcha.siteKey(), "RECAPTCHA_SITE_KEY");
      require(problems, captcha.secretKey(), "RECAPTCHA_SECRET_KEY");
    }
    if (production && !organizer.httpsOnly()) {
      problems.add("ORGANIZER_HTTPS_ONLY may be false only on the local stack");
    }
    String options = properties.optionsFile();
    if (production && (options == null || options.isBlank() || options.startsWith("classpath:"))) {
      problems.add("CONFERENCE_OPTIONS_FILE must be set in production");
    }
    AppProperties.RateLimit limits = properties.rateLimit();
    if (properties.maxRequestBytes() <= 0
        || limits.registrations() <= 0
        || limits.tokens() <= 0
        || limits.exports() <= 0
        || limits.formConfig() <= 0) {
      problems.add("MAX_REQUEST_BYTES and RATE_LIMIT_* must be positive");
    }
    return problems;
  }

  private static void require(List<String> problems, String value, String name) {
    if (value == null || value.isBlank()) {
      problems.add(name + " must be set");
    }
  }
}

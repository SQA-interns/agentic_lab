package si.konferenca.registration.config;

import java.util.List;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Stops the application when {@link StartupChecks} finds a problem (SR-02, section 8). */
@Component
public class StartupValidator implements InitializingBean {

  private final AppProperties properties;
  private final String smtpHost;

  public StartupValidator(
      AppProperties properties, @Value("${spring.mail.host:}") String smtpHost) {
    this.properties = properties;
    this.smtpHost = smtpHost;
  }

  @Override
  public void afterPropertiesSet() {
    List<String> problems = StartupChecks.problems(properties, smtpHost);
    if (!problems.isEmpty()) {
      throw new IllegalStateException("Invalid configuration: " + String.join("; ", problems));
    }
  }
}

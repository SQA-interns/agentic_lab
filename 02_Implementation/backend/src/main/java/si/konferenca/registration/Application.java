package si.konferenca.registration;

import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Application {

  /**
   * Local-development defaults (specification §9). They have the lowest precedence, so any
   * environment variable (e.g. {@code SPRING_DATASOURCE_URL}, {@code APP_BACKUP_DIR}) overrides
   * them. Credentials (database, organizer) and organizer recipients deliberately have no default.
   */
  static final Map<String, Object> DEFAULTS =
      Map.of(
          "spring.datasource.url", "jdbc:postgresql://localhost:5432/conference",
          "spring.mail.host", "localhost",
          "spring.mail.port", "1025",
          "app.options.file", "./config/conference-options.json",
          "app.backup.dir", "./data/registrations",
          "app.recaptcha.test-mode", "${RECAPTCHA_TEST_MODE:false}");

  public static void main(String[] args) {
    SpringApplication application = new SpringApplication(Application.class);
    application.setDefaultProperties(DEFAULTS);
    application.run(args);
  }
}

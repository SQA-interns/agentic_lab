package si.konferenca.registration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import si.konferenca.registration.domain.ConferenceOption;
import si.konferenca.registration.domain.Registration;
import si.konferenca.registration.domain.RegistrationRepository;
import si.konferenca.registration.domain.RegistrationType;
import si.konferenca.registration.service.FormConfigService;
import si.konferenca.registration.service.NewRegistration;
import si.konferenca.registration.service.RegistrationResult;
import si.konferenca.registration.service.RegistrationService;
import si.konferenca.registration.support.PostgresIntegrationTest;
import si.konferenca.registration.support.TestData;

/**
 * AC-005-02 (registrations survive a restart), AC-003-03 (options change through configuration
 * only) and AC-003-05 (stored registrations keep the stable option id after a rename).
 */
class RestartAndReconfigurationIntegrationTest {

  @TempDir Path tempDir;

  private ConfigurableApplicationContext start(Path optionsFile, Path backupDir) {
    // Command-line arguments take precedence over application.yml (default properties do not).
    return new SpringApplicationBuilder(Application.class)
        .web(WebApplicationType.SERVLET)
        .run(
            "--spring.datasource.url=" + PostgresIntegrationTest.POSTGRES.getJdbcUrl(),
            "--spring.datasource.username=" + PostgresIntegrationTest.POSTGRES.getUsername(),
            "--spring.datasource.password=" + PostgresIntegrationTest.POSTGRES.getPassword(),
            "--app.recaptcha.test-mode=true",
            "--app.options.file=" + optionsFile,
            "--app.backup.dir=" + backupDir,
            "--spring.mail.host=127.0.0.1",
            "--spring.mail.port=9",
            "--server.port=0");
  }

  private static void writeOptions(Path file, String json) throws Exception {
    Files.writeString(file, json, StandardCharsets.UTF_8);
  }

  @Test
  void registrationsSurviveRestartAndOptionsFollowConfiguration() throws Exception {
    Path optionsFile = tempDir.resolve("options.json");
    Path backupDir = tempDir.resolve("backups");
    writeOptions(
        optionsFile,
        """
        { "options": [
          { "id": "ws-alpha", "name": "Workshop Alpha", "category": "WORKSHOP", "active": true },
          { "id": "ev-gala", "name": "Gala", "category": "EVENT", "active": true }
        ] }
        """);

    UUID id;
    try (ConfigurableApplicationContext first = start(optionsFile, backupDir)) {
      first.getBean(RegistrationRepository.class).deleteAll();
      assertThat(first.getBean(FormConfigService.class).formConfig().options())
          .extracting(ConferenceOption::id)
          .containsExactly("ws-alpha", "ev-gala");
      RegistrationResult result =
          first
              .getBean(RegistrationService.class)
              .register(
                  new NewRegistration(
                      RegistrationType.EXTERNAL,
                      "Maja",
                      "Zupan",
                      "maja@example.si",
                      "Zavod Ž",
                      null,
                      null,
                      null,
                      List.of("ws-alpha"),
                      Map.of("privacy", true),
                      TestData.CAPTCHA_TOKEN,
                      "127.0.0.1"));
      id = result.registrationId();
    }

    // The organizer renames ws-alpha, deactivates ev-gala and adds a meal - configuration only.
    writeOptions(
        optionsFile,
        """
        { "options": [
          { "id": "ws-alpha", "name": "Workshop Alpha (renamed)", "category": "WORKSHOP", "active": true },
          { "id": "ev-gala", "name": "Gala", "category": "EVENT", "active": false },
          { "id": "meal-brunch", "name": "Brunch", "category": "MEAL", "active": true }
        ] }
        """);

    try (ConfigurableApplicationContext second = start(optionsFile, backupDir)) {
      Registration stored = second.getBean(RegistrationRepository.class).findById(id).orElseThrow();
      assertThat(stored.getFirstName()).isEqualTo("Maja");
      assertThat(stored.getOrganization()).isEqualTo("Zavod Ž");
      assertThat(stored.getOptions()).extracting(o -> o.getOptionId()).containsExactly("ws-alpha");

      FormConfigService.FormConfig config = second.getBean(FormConfigService.class).formConfig();
      assertThat(config.options())
          .extracting(ConferenceOption::id)
          .containsExactly("ws-alpha", "meal-brunch");
      assertThat(config.options().get(0).name()).isEqualTo("Workshop Alpha (renamed)");
      second.getBean(RegistrationRepository.class).deleteAll();
    }
  }
}

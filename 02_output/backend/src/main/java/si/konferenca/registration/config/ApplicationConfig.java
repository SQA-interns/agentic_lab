package si.konferenca.registration.config;

import java.nio.file.Path;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import si.konferenca.registration.adapter.in.web.FormController.CaptchaResponse;
import si.konferenca.registration.adapter.in.web.FormController.ConsentResponse;
import si.konferenca.registration.adapter.in.web.FormController.FormConfigResponse;
import si.konferenca.registration.adapter.out.options.FileOptionsCatalogue;
import si.konferenca.registration.application.FormQueries;
import si.konferenca.registration.domain.OptionsCatalogue;

/** Wires the use cases to their adapters and settings. */
@Configuration
@EnableConfigurationProperties(AppProperties.class)
public class ApplicationConfig {

  /** The identifier of the one mandatory consent (D-09). */
  public static final String CONSENT_ID = "personal-data";

  @Bean
  OptionsCatalogue optionsCatalogue(AppProperties properties) {
    return FileOptionsCatalogue.load(Path.of(properties.optionsFile()));
  }

  @Bean
  FormQueries formQueries(OptionsCatalogue catalogue) {
    return new FormQueries(catalogue);
  }

  @Bean
  FormConfigResponse formConfigResponse(AppProperties properties) {
    boolean testMode = properties.recaptcha().testMode();
    return new FormConfigResponse(
        properties.conferenceName(),
        new ConsentResponse(CONSENT_ID, properties.consentText()),
        new CaptchaResponse(
            testMode ? "test" : "recaptcha", testMode ? "" : properties.recaptcha().siteKey()));
  }
}

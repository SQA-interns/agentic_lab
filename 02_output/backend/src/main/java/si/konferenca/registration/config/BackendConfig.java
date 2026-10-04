package si.konferenca.registration.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;
import si.konferenca.registration.application.OptionsCatalog;
import si.konferenca.registration.application.PublicSettings;
import si.konferenca.registration.infrastructure.JsonOptionsFile;
import tools.jackson.databind.json.JsonMapper;

/** Wires the ports to their adapters from the application settings. */
@Configuration
public class BackendConfig {

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }

  @Bean
  OptionsCatalog optionsCatalog(
      ResourceLoader loader, JsonMapper mapper, AppProperties properties) {
    return new JsonOptionsFile(loader, mapper, properties.optionsFile());
  }

  @Bean
  PublicSettings publicSettings(AppProperties properties) {
    return new PublicSettings(
        properties.conferenceName(),
        properties.captcha().testMode(),
        properties.captcha().siteKey());
  }
}

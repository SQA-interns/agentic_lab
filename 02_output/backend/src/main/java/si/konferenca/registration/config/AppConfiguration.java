package si.konferenca.registration.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import si.konferenca.registration.domain.ConferenceCatalog;

/** Checks the settings at startup and loads the conference catalog. */
@Configuration
@EnableConfigurationProperties(AppProperties.class)
public class AppConfiguration {

  @Bean
  ConferenceCatalog conferenceCatalog(AppProperties app) {
    StartupGuard.check(app);
    return new ConferenceCatalogLoader().load(app.conferenceConfigFile());
  }
}

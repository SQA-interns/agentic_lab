package si.konferenca.registration.config;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import si.konferenca.registration.application.RegistrationFormService;
import si.konferenca.registration.domain.OptionCatalogue;
import si.konferenca.registration.infrastructure.OptionsFileLoader;

/** Wires the application services and their infrastructure adapters from {@link AppProperties}. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AppProperties.class)
public class AppConfiguration {

  private final AppProperties properties;

  public AppConfiguration(AppProperties properties, Environment environment) {
    ConfigurationGuard.check(properties, environment);
    this.properties = properties;
  }

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }

  @Bean
  OptionCatalogue optionCatalogue(ResourceLoader resourceLoader) {
    return new OptionsFileLoader().load(resourceLoader.getResource(properties.optionsFile()));
  }

  @Bean
  RegistrationFormService registrationFormService(OptionCatalogue catalogue) {
    return new RegistrationFormService(
        catalogue,
        properties.conferenceName(),
        properties.recaptcha().testMode()
            ? RegistrationFormService.CaptchaMode.TEST
            : RegistrationFormService.CaptchaMode.RECAPTCHA,
        properties.recaptcha().siteKey());
  }
}

package si.konferenca.registration.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import si.konferenca.registration.application.GetRegistrationForm;
import si.konferenca.registration.domain.ConferenceCatalogue;

/** Wires settings, the options catalogue and the adapters (specification sections 2 and 3). */
@Configuration
public class ApplicationConfig {

  @Bean
  AppSettings appSettings(Environment environment) {
    return AppSettings.from(environment);
  }

  @Bean
  ConferenceCatalogue conferenceCatalogue(AppSettings settings) {
    return ConferenceConfigLoader.load(settings.conferenceConfigPath());
  }



  @Bean
  GetRegistrationForm.CaptchaSettings captchaSettings(AppSettings settings) {
    return new GetRegistrationForm.CaptchaSettings(
        settings.recaptchaTestMode(), settings.recaptchaSiteKey());
  }




}

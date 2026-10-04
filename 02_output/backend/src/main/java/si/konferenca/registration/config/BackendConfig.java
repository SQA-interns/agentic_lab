package si.konferenca.registration.config;

import java.nio.file.Path;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;
import org.springframework.transaction.support.TransactionTemplate;
import si.konferenca.registration.application.CaptchaVerifier;
import si.konferenca.registration.application.JsonCopyStore;
import si.konferenca.registration.application.OptionsCatalog;
import si.konferenca.registration.application.PublicSettings;
import si.konferenca.registration.application.RegistrationJson;
import si.konferenca.registration.application.RegistrationNotifier;
import si.konferenca.registration.application.RegistrationService;
import si.konferenca.registration.domain.RegistrationRepository;
import si.konferenca.registration.infrastructure.FileJsonCopyStore;
import si.konferenca.registration.infrastructure.JsonOptionsFile;
import si.konferenca.registration.infrastructure.RecaptchaVerifier;
import si.konferenca.registration.infrastructure.TestModeCaptchaVerifier;
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
  JsonCopyStore jsonCopyStore(AppProperties properties) {
    return new FileJsonCopyStore(Path.of(properties.jsonCopyDir()));
  }

  @Bean
  CaptchaVerifier captchaVerifier(AppProperties properties, JsonMapper mapper) {
    AppProperties.Captcha captcha = properties.captcha();
    return captcha.testMode()
        ? new TestModeCaptchaVerifier()
        : new RecaptchaVerifier(captcha.verifyUrl(), captcha.secretKey(), mapper);
  }

  @Bean
  RegistrationService registrationService(
      RegistrationRepository repository,
      JsonCopyStore copies,
      CaptchaVerifier captcha,
      OptionsCatalog catalog,
      JsonMapper mapper,
      RegistrationNotifier notifier,
      TransactionTemplate transaction,
      Clock clock) {
    return new RegistrationService(
        repository,
        copies,
        captcha,
        catalog,
        new RegistrationJson(mapper),
        notifier,
        transaction,
        clock);
  }

  @Bean
  PublicSettings publicSettings(AppProperties properties) {
    return new PublicSettings(
        properties.conferenceName(),
        properties.captcha().testMode(),
        properties.captcha().siteKey());
  }
}

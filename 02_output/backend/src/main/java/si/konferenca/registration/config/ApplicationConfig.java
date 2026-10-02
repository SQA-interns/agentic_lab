package si.konferenca.registration.config;

import java.nio.file.Path;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import si.konferenca.registration.adapter.in.web.FormController.CaptchaResponse;
import si.konferenca.registration.adapter.in.web.FormController.ConsentResponse;
import si.konferenca.registration.adapter.in.web.FormController.FormConfigResponse;
import si.konferenca.registration.adapter.in.web.RegistrationController.RequestLimits;
import si.konferenca.registration.adapter.out.captcha.RecaptchaCaptchaVerifier;
import si.konferenca.registration.adapter.out.captcha.TestModeCaptchaVerifier;
import si.konferenca.registration.adapter.out.options.FileOptionsCatalogue;
import si.konferenca.registration.adapter.out.persistence.JpaRegistrationStore;
import si.konferenca.registration.adapter.out.persistence.TransactionalUnitOfWork;
import si.konferenca.registration.application.FormQueries;
import si.konferenca.registration.application.SubmitRegistration;
import si.konferenca.registration.application.SubmitRegistration.ConsentTerms;
import si.konferenca.registration.domain.CaptchaVerifier;
import si.konferenca.registration.domain.OptionsCatalogue;
import si.konferenca.registration.domain.RegistrationStore;
import si.konferenca.registration.domain.RegistrationValidator;
import si.konferenca.registration.domain.UnitOfWork;

/** Wires the use cases to their adapters and settings. */
@Configuration
@EnableConfigurationProperties(AppProperties.class)
public class ApplicationConfig {

  /** The identifier of the one mandatory consent (D-09). */
  public static final String CONSENT_ID = "personal-data";

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }

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

  @Bean
  CaptchaVerifier captchaVerifier(AppProperties properties) {
    if (properties.recaptcha().testMode()) {
      return new TestModeCaptchaVerifier();
    }
    return new RecaptchaCaptchaVerifier(
        properties.recaptcha().verifyUrl(), properties.recaptcha().secretKey());
  }

  @Bean
  RegistrationStore registrationStore() {
    return new JpaRegistrationStore();
  }

  @Bean
  UnitOfWork unitOfWork(PlatformTransactionManager transactionManager) {
    return new TransactionalUnitOfWork(transactionManager);
  }

  @Bean
  SubmitRegistration submitRegistration(
      OptionsCatalogue catalogue,
      CaptchaVerifier captchaVerifier,
      UnitOfWork unitOfWork,
      RegistrationStore store,
      AppProperties properties,
      Clock clock) {
    return new SubmitRegistration(
        new RegistrationValidator(catalogue),
        captchaVerifier,
        unitOfWork,
        store,
        new ConsentTerms(CONSENT_ID, properties.consentText()),
        clock);
  }

  @Bean
  RequestLimits requestLimits(AppProperties properties) {
    return new RequestLimits(properties.maxRequestBytes());
  }
}

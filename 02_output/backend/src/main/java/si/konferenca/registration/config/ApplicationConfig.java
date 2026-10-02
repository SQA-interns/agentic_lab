package si.konferenca.registration.config;

import java.nio.file.Path;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.transaction.PlatformTransactionManager;
import si.konferenca.registration.adapter.in.web.FormController.CaptchaResponse;
import si.konferenca.registration.adapter.in.web.FormController.ConsentResponse;
import si.konferenca.registration.adapter.in.web.FormController.FormConfigResponse;
import si.konferenca.registration.adapter.in.web.RegistrationController.RequestLimits;
import si.konferenca.registration.adapter.out.captcha.RecaptchaCaptchaVerifier;
import si.konferenca.registration.adapter.out.captcha.TestModeCaptchaVerifier;
import si.konferenca.registration.adapter.out.excel.PoiRegistrationExporter;
import si.konferenca.registration.adapter.out.jsoncopy.FileJsonCopyStore;
import si.konferenca.registration.adapter.out.mail.SmtpMailNotifier;
import si.konferenca.registration.adapter.out.options.FileOptionsCatalogue;
import si.konferenca.registration.adapter.out.persistence.JpaRegistrationStore;
import si.konferenca.registration.adapter.out.persistence.TransactionalUnitOfWork;
import si.konferenca.registration.application.ExportRegistrations;
import si.konferenca.registration.application.FormQueries;
import si.konferenca.registration.application.SubmitRegistration;
import si.konferenca.registration.application.SubmitRegistration.ConsentTerms;
import si.konferenca.registration.domain.CaptchaVerifier;
import si.konferenca.registration.domain.JsonCopyStore;
import si.konferenca.registration.domain.MailNotifier;
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

  /** Refuses to start with settings the specification forbids (SR-02, SR-06). */
  @Bean
  InitializingBean settingsVerified(AppProperties properties) {
    return () -> StartupChecks.verify(properties);
  }

  @Bean
  ExportRegistrations exportRegistrations(RegistrationStore store) {
    return new ExportRegistrations(store, new PoiRegistrationExporter());
  }

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
  JsonCopyStore jsonCopyStore(AppProperties properties) {
    return FileJsonCopyStore.open(Path.of(properties.jsonCopyDir()));
  }

  @Bean
  MailNotifier mailNotifier(JavaMailSenderImpl sender, AppProperties properties) {
    // An SMTP account is used only when one is configured (specification section 5).
    String username = sender.getUsername();
    boolean authenticate = username != null && !username.isBlank();
    sender.getJavaMailProperties().setProperty("mail.smtp.auth", String.valueOf(authenticate));
    return new SmtpMailNotifier(
        sender,
        properties.mailFrom(),
        organizerRecipients(properties),
        properties.conferenceName());
  }

  static List<String> organizerRecipients(AppProperties properties) {
    return Arrays.stream(properties.organizerEmails().split(","))
        .map(String::strip)
        .filter(address -> !address.isEmpty())
        .toList();
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
      JsonCopyStore jsonCopies,
      MailNotifier mailNotifier,
      AppProperties properties,
      Clock clock) {
    return new SubmitRegistration(
        new RegistrationValidator(catalogue),
        captchaVerifier,
        unitOfWork,
        store,
        jsonCopies,
        mailNotifier,
        new ConsentTerms(CONSENT_ID, properties.consentText()),
        clock);
  }

  @Bean
  RequestLimits requestLimits(AppProperties properties) {
    return new RequestLimits(properties.maxRequestBytes());
  }
}

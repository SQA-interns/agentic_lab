package si.konferenca.registration.config;

import jakarta.persistence.EntityManager;
import java.nio.file.Path;
import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import si.konferenca.registration.application.ExportService;
import si.konferenca.registration.application.RegistrationEmails;
import si.konferenca.registration.application.RegistrationPorts.CaptchaVerifier;
import si.konferenca.registration.application.RegistrationPorts.JsonCopyStore;
import si.konferenca.registration.application.RegistrationPorts.Notifier;
import si.konferenca.registration.application.RegistrationPorts.RegistrationRepository;
import si.konferenca.registration.application.RegistrationService;
import si.konferenca.registration.domain.ConferenceCatalog;
import si.konferenca.registration.domain.RegistrationValidator;
import si.konferenca.registration.infrastructure.captcha.RecaptchaVerifier;
import si.konferenca.registration.infrastructure.captcha.TestModeCaptchaVerifier;
import si.konferenca.registration.infrastructure.copy.FileJsonCopyStore;
import si.konferenca.registration.infrastructure.export.PoiExportWriter;
import si.konferenca.registration.infrastructure.mail.SmtpNotifier;
import si.konferenca.registration.infrastructure.persistence.JpaRegistrationRepository;
import si.konferenca.registration.infrastructure.persistence.SpringTransactions;

/** Wires the registration use case to its adapters (docs/02_specification.md §2, §3). */
@Configuration
public class RegistrationConfig {

  @Bean
  RegistrationRepository registrationRepository(
      EntityManager entityManager, PlatformTransactionManager transactionManager) {
    TransactionTemplate readOnly = new TransactionTemplate(transactionManager);
    readOnly.setReadOnly(true);
    return new JpaRegistrationRepository(entityManager, readOnly);
  }

  @Bean
  JsonCopyStore jsonCopyStore(AppProperties properties) {
    FileJsonCopyStore store = new FileJsonCopyStore(Path.of(properties.jsonCopyDir()));
    store.prepare();
    return store;
  }

  @Bean
  CaptchaVerifier captchaVerifier(AppProperties properties) {
    AppProperties.Recaptcha captcha = properties.recaptcha();
    return captcha.testMode()
        ? new TestModeCaptchaVerifier()
        : new RecaptchaVerifier(captcha.verifyUrl(), captcha.secretKey());
  }

  @Bean
  RegistrationEmails registrationEmails(AppProperties properties) {
    return new RegistrationEmails(properties.conferenceName(), properties.organizer().emailList());
  }

  @Bean
  Notifier notifier(
      JavaMailSender sender,
      RegistrationEmails emails,
      AppProperties properties,
      Executor mailExecutor) {
    return new SmtpNotifier(sender, emails, properties.mailFrom(), mailExecutor);
  }

  @Bean
  ExportService exportService(RegistrationRepository repository) {
    return new ExportService(repository, new PoiExportWriter());
  }

  @Bean
  RegistrationService registrationService(
      ConferenceCatalog catalog,
      CaptchaVerifier captcha,
      RegistrationRepository repository,
      JsonCopyStore copies,
      PlatformTransactionManager transactionManager,
      Notifier notifier,
      Clock clock) {
    return new RegistrationService(
        new RegistrationValidator(catalog),
        captcha,
        repository,
        copies,
        new SpringTransactions(new TransactionTemplate(transactionManager)),
        notifier,
        clock,
        UUID::randomUUID);
  }
}

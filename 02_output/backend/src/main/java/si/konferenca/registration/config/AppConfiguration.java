package si.konferenca.registration.config;

import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.util.Properties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import si.konferenca.registration.application.CaptchaVerifier;
import si.konferenca.registration.application.ExportService;
import si.konferenca.registration.application.RegistrationCopyStore;
import si.konferenca.registration.application.RegistrationFormService;
import si.konferenca.registration.application.RegistrationNotifier;
import si.konferenca.registration.application.RegistrationRepository;
import si.konferenca.registration.application.RegistrationService;
import si.konferenca.registration.application.RegistrationValidator;
import si.konferenca.registration.domain.OptionCatalogue;
import si.konferenca.registration.infrastructure.FileRegistrationCopyStore;
import si.konferenca.registration.infrastructure.JpaRegistrationRepository;
import si.konferenca.registration.infrastructure.OptionsFileLoader;
import si.konferenca.registration.infrastructure.PoiWorkbookWriter;
import si.konferenca.registration.infrastructure.RecaptchaCaptchaVerifier;
import si.konferenca.registration.infrastructure.SmtpRegistrationNotifier;
import si.konferenca.registration.infrastructure.TestModeCaptchaVerifier;

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
  RegistrationValidator registrationValidator(OptionCatalogue catalogue) {
    return new RegistrationValidator(catalogue);
  }

  @Bean
  CaptchaVerifier captchaVerifier() {
    AppProperties.Recaptcha recaptcha = properties.recaptcha();
    return recaptcha.testMode()
        ? new TestModeCaptchaVerifier()
        : new RecaptchaCaptchaVerifier(recaptcha.verifyUrl(), recaptcha.secretKey());
  }

  @Bean
  RegistrationRepository registrationRepository(EntityManager entityManager) {
    return new JpaRegistrationRepository(entityManager);
  }

  @Bean
  RegistrationCopyStore registrationCopyStore() {
    return new FileRegistrationCopyStore(properties.jsonCopyDir());
  }

  /** SMTP client; STARTTLS is required when SMTP_TLS is true (SB-04). */
  @Bean
  JavaMailSender mailSender() {
    AppProperties.Smtp smtp = properties.smtp();
    JavaMailSenderImpl sender = new JavaMailSenderImpl();
    sender.setHost(smtp.host());
    sender.setPort(smtp.port());
    sender.setDefaultEncoding("UTF-8");
    boolean authenticate = smtp.username() != null && !smtp.username().isBlank();
    if (authenticate) {
      sender.setUsername(smtp.username());
      sender.setPassword(smtp.password());
    }
    Properties mail = sender.getJavaMailProperties();
    mail.setProperty("mail.smtp.auth", String.valueOf(authenticate));
    mail.setProperty("mail.smtp.starttls.enable", String.valueOf(smtp.tls()));
    mail.setProperty("mail.smtp.starttls.required", String.valueOf(smtp.tls()));
    mail.setProperty("mail.smtp.connectiontimeout", "5000");
    mail.setProperty("mail.smtp.timeout", "10000");
    mail.setProperty("mail.smtp.writetimeout", "10000");
    return sender;
  }

  @Bean
  RegistrationNotifier registrationNotifier(JavaMailSender mailSender) {
    return new SmtpRegistrationNotifier(
        mailSender,
        properties.mailFrom(),
        properties.conferenceName(),
        properties.organizer().emails());
  }

  @Bean
  RegistrationService registrationService(
      RegistrationValidator validator,
      CaptchaVerifier captchaVerifier,
      RegistrationRepository repository,
      RegistrationCopyStore copyStore,
      RegistrationNotifier notifier,
      PlatformTransactionManager transactionManager,
      Clock clock) {
    return new RegistrationService(
        validator,
        captchaVerifier,
        repository,
        copyStore,
        notifier,
        new TransactionTemplate(transactionManager),
        clock);
  }

  @Bean
  ExportService exportService(
      RegistrationRepository repository, PlatformTransactionManager transactionManager) {
    TransactionTemplate readOnly = new TransactionTemplate(transactionManager);
    readOnly.setReadOnly(true);
    return new ExportService(repository, new PoiWorkbookWriter(), readOnly);
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

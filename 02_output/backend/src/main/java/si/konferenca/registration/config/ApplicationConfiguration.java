package si.konferenca.registration.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Properties;
import java.util.concurrent.Executor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.support.TransactionTemplate;
import si.konferenca.registration.application.CaptchaVerifier;
import si.konferenca.registration.application.ExportService;
import si.konferenca.registration.application.JsonCopyStore;
import si.konferenca.registration.application.Notifier;
import si.konferenca.registration.application.RegistrationService;
import si.konferenca.registration.application.RegistrationStore;
import si.konferenca.registration.application.RegistrationValidator;
import si.konferenca.registration.application.SetupQuery;
import si.konferenca.registration.application.WorkbookWriter;
import si.konferenca.registration.domain.OptionCatalog;
import si.konferenca.registration.infrastructure.excel.PoiWorkbookWriter;
import si.konferenca.registration.infrastructure.jsoncopy.FileJsonCopyStore;
import si.konferenca.registration.infrastructure.mail.SmtpNotifier;
import si.konferenca.registration.infrastructure.options.OptionCatalogLoader;
import si.konferenca.registration.infrastructure.persistence.JpaRegistrationStore;
import si.konferenca.registration.infrastructure.persistence.RegistrationJpaRepository;
import si.konferenca.registration.infrastructure.recaptcha.RecaptchaVerifier;

/** Wires the layers (spec section 2): ports of the application to infrastructure adapters. */
@Configuration
@EnableConfigurationProperties(AppProperties.class)
public class ApplicationConfiguration {

  private static final int MAIL_TIMEOUT_MILLIS = 10_000;

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }

  @Bean
  OptionCatalog optionCatalog(ResourceLoader loader, AppProperties p) {
    return OptionCatalogLoader.load(loader, p.optionsFile());
  }

  @Bean
  CaptchaVerifier captchaVerifier(AppProperties p) {
    AppProperties.Recaptcha r = p.recaptcha();
    return new RecaptchaVerifier(r.testMode(), r.siteKey(), r.secretKey(), r.verifyUrl());
  }

  @Bean
  JsonCopyStore jsonCopyStore(AppProperties p) {
    Path dir = Path.of(p.jsonCopyDir());
    try {
      Files.createDirectories(dir);
    } catch (IOException e) {
      throw new UncheckedIOException("JSON copy directory cannot be created", e);
    }
    return new FileJsonCopyStore(dir);
  }

  @Bean
  RegistrationStore registrationStore(RegistrationJpaRepository repository) {
    return new JpaRegistrationStore(repository);
  }

  @Bean
  JavaMailSender mailSender(AppProperties p) {
    AppProperties.Smtp s = p.smtp();
    JavaMailSenderImpl sender = new JavaMailSenderImpl();
    sender.setHost(s.host());
    sender.setPort(s.port());
    sender.setDefaultEncoding("UTF-8");
    boolean auth = s.username() != null && !s.username().isBlank();
    if (auth) {
      sender.setUsername(s.username());
      sender.setPassword(s.password());
    }
    Properties props = sender.getJavaMailProperties();
    props.put("mail.smtp.auth", String.valueOf(auth));
    props.put("mail.smtp.starttls.enable", String.valueOf(s.starttls()));
    props.put("mail.smtp.starttls.required", String.valueOf(s.starttls()));
    props.put("mail.smtp.connectiontimeout", String.valueOf(MAIL_TIMEOUT_MILLIS));
    props.put("mail.smtp.timeout", String.valueOf(MAIL_TIMEOUT_MILLIS));
    props.put("mail.smtp.writetimeout", String.valueOf(MAIL_TIMEOUT_MILLIS));
    return sender;
  }

  @Bean
  ThreadPoolTaskExecutor mailExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(4);
    executor.setQueueCapacity(1000);
    executor.setThreadNamePrefix("mail-");
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(30);
    return executor;
  }

  @Bean
  Notifier notifier(JavaMailSender sender, Executor mailExecutor, AppProperties p) {
    return new SmtpNotifier(
        sender, mailExecutor, p.mailFrom(), p.organizerEmails(), p.conferenceName());
  }

  @Bean
  WorkbookWriter workbookWriter() {
    return new PoiWorkbookWriter();
  }

  @Bean
  RegistrationService registrationService(
      OptionCatalog catalog,
      CaptchaVerifier captcha,
      RegistrationStore store,
      JsonCopyStore copies,
      Notifier notifier,
      TransactionTemplate transaction,
      Clock clock) {
    return new RegistrationService(
        new RegistrationValidator(catalog), captcha, store, copies, notifier, transaction, clock);
  }

  @Bean
  SetupQuery setupQuery(AppProperties p, OptionCatalog catalog, CaptchaVerifier captcha) {
    return new SetupQuery(p.conferenceName(), catalog, captcha);
  }

  @Bean
  ExportService exportService(
      RegistrationStore store, WorkbookWriter writer, TransactionTemplate transaction) {
    return new ExportService(store, writer, transaction);
  }
}

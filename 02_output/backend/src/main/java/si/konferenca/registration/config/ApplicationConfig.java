package si.konferenca.registration.config;

import java.net.URI;
import java.nio.file.Path;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import si.konferenca.registration.application.CaptchaVerifier;
import si.konferenca.registration.application.ConferenceSettings;
import si.konferenca.registration.application.JsonCopyStore;
import si.konferenca.registration.application.Mailer;
import si.konferenca.registration.application.NotificationService;
import si.konferenca.registration.application.RegistrationStore;
import si.konferenca.registration.application.WorkbookWriter;
import si.konferenca.registration.domain.OptionsCatalog;
import si.konferenca.registration.infrastructure.FileJsonCopyStore;
import si.konferenca.registration.infrastructure.JpaRegistrationRepository;
import si.konferenca.registration.infrastructure.JpaRegistrationStore;
import si.konferenca.registration.infrastructure.JsonCopyDirectoryHealthIndicator;
import si.konferenca.registration.infrastructure.OptionsFileLoader;
import si.konferenca.registration.infrastructure.PoiWorkbookWriter;
import si.konferenca.registration.infrastructure.RecaptchaVerifier;
import si.konferenca.registration.infrastructure.SmtpMailer;
import si.konferenca.registration.infrastructure.TestModeCaptchaVerifier;

/** Wires the ports to their adapters from configuration (specification sections 2 and 3). */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(AppProperties.class)
public class ApplicationConfig {

  private final AppProperties properties;

  public ApplicationConfig(AppProperties properties) {
    StartupChecks.verify(properties);
    this.properties = properties;
  }

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }

  @Bean
  OptionsCatalog optionsCatalog() {
    return OptionsFileLoader.load(Path.of(properties.optionsFile()));
  }

  @Bean
  ConferenceSettings conferenceSettings() {
    return new ConferenceSettings(
        properties.conferenceName(),
        properties.organizer().emailList(),
        properties.recaptcha().testMode() ? "" : properties.recaptcha().siteKey(),
        properties.mail().maxAttempts());
  }

  @Bean
  CaptchaVerifier captchaVerifier() {
    AppProperties.Recaptcha captcha = properties.recaptcha();
    return captcha.testMode()
        ? new TestModeCaptchaVerifier()
        : new RecaptchaVerifier(URI.create(captcha.verifyUrl()), captcha.secretKey());
  }

  @Bean
  RegistrationStore registrationStore(JpaRegistrationRepository repository) {
    return new JpaRegistrationStore(repository);
  }

  @Bean
  JsonCopyStore jsonCopyStore() {
    return new FileJsonCopyStore(Path.of(properties.jsonCopyDir()));
  }

  @Bean
  Mailer mailer(JavaMailSender sender) {
    return new SmtpMailer(sender, properties.mail().from());
  }

  @Bean
  WorkbookWriter workbookWriter() {
    return new PoiWorkbookWriter();
  }

  @Bean("jsonCopyDirectory")
  JsonCopyDirectoryHealthIndicator jsonCopyDirectoryHealthIndicator(JsonCopyStore copies) {
    return new JsonCopyDirectoryHealthIndicator(copies);
  }

  @Bean
  MailRetryJob mailRetryJob(NotificationService notifications) {
    return new MailRetryJob(notifications);
  }

  /** Retries failed or interrupted emails (D-11). */
  static final class MailRetryJob {

    private final NotificationService notifications;

    MailRetryJob(NotificationService notifications) {
      this.notifications = notifications;
    }

    @Scheduled(
        fixedDelayString = "${app.mail.retry-interval:PT5M}",
        initialDelayString = "${app.mail.retry-interval:PT5M}")
    void retry() {
      notifications.retryDue();
    }
  }
}

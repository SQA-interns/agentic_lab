package si.konferenca.registration.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.time.Clock;
import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import si.konferenca.registration.adapter.captcha.GoogleCaptchaVerifier;
import si.konferenca.registration.adapter.captcha.TestModeCaptchaVerifier;
import si.konferenca.registration.adapter.jsoncopy.FileJsonCopyStore;
import si.konferenca.registration.adapter.mail.RegistrationMailer;
import si.konferenca.registration.application.CaptchaVerifier;
import si.konferenca.registration.application.GetRegistrationForm;
import si.konferenca.registration.application.JsonCopyStore;
import si.konferenca.registration.domain.ConferenceCatalogue;
import si.konferenca.registration.domain.RegistrationValidator;

/** Wires settings, the options catalogue and the adapters (specification sections 2 and 3). */
@Configuration
@EnableAsync
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
  Clock clock() {
    return Clock.systemUTC();
  }

  @Bean
  RegistrationValidator registrationValidator(ConferenceCatalogue catalogue, Clock clock) {
    return new RegistrationValidator(catalogue, clock);
  }

  @Bean
  GetRegistrationForm.CaptchaSettings captchaSettings(AppSettings settings) {
    return new GetRegistrationForm.CaptchaSettings(
        settings.recaptchaTestMode(), settings.recaptchaSiteKey());
  }

  @Bean
  CaptchaVerifier captchaVerifier(AppSettings settings) {
    if (settings.recaptchaTestMode()) {
      return new TestModeCaptchaVerifier();
    }
    return new GoogleCaptchaVerifier(settings.recaptchaVerifyUrl(), settings.recaptchaSecretKey());
  }

  @Bean
  JsonCopyStore jsonCopyStore(AppSettings settings) {
    try {
      Files.createDirectories(settings.jsonCopyDir());
    } catch (IOException e) {
      throw new UncheckedIOException("JSON_COPY_DIR cannot be created", e);
    }
    return new FileJsonCopyStore(settings.jsonCopyDir());
  }

  @Bean
  RegistrationMailer registrationMailer(JavaMailSender sender, AppSettings settings) {
    return new RegistrationMailer(
        sender, settings.mailFrom(), settings.conferenceName(), settings.organizerEmails());
  }

  @Bean(name = "taskExecutor")
  Executor taskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(4);
    executor.setQueueCapacity(500);
    executor.setThreadNamePrefix("mail-");
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(30);
    executor.initialize();
    return executor;
  }
}

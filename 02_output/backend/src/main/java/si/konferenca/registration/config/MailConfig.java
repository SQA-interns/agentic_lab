package si.konferenca.registration.config;

import java.util.concurrent.Executor;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** SMTP authentication follows SMTP_USERNAME; emails are sent on their own threads (D-07). */
@Configuration
public class MailConfig {

  /** Authenticates only when a username is configured (Mailpit locally needs none). */
  @Bean
  static BeanPostProcessor smtpAuthentication() {
    return new BeanPostProcessor() {
      @Override
      public Object postProcessBeforeInitialization(Object bean, String beanName) {
        if (bean instanceof JavaMailSenderImpl sender) {
          String username = sender.getUsername();
          sender
              .getJavaMailProperties()
              .setProperty(
                  "mail.smtp.auth", String.valueOf(username != null && !username.isBlank()));
        }
        return bean;
      }
    };
  }

  @Bean
  Executor mailExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setThreadNamePrefix("mail-");
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(4);
    executor.setQueueCapacity(1000);
    executor.initialize();
    return executor;
  }
}

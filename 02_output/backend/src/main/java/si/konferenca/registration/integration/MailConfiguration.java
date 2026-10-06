package si.konferenca.registration.integration;

import java.nio.charset.StandardCharsets;
import java.util.Properties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import si.konferenca.registration.config.AppProperties;

/** SMTP connection from `app.mail` (spec section 4); 10 s timeouts (D-13). */
@Configuration
public class MailConfiguration {

  private static final String TIMEOUT_MS = "10000";

  @Bean
  JavaMailSender mailSender(AppProperties app) {
    AppProperties.Mail mail = app.mail();
    JavaMailSenderImpl sender = new JavaMailSenderImpl();
    sender.setHost(mail.host());
    sender.setPort(mail.port());
    sender.setDefaultEncoding(StandardCharsets.UTF_8.name());
    Properties properties = sender.getJavaMailProperties();
    boolean authenticate = mail.username() != null && !mail.username().isBlank();
    if (authenticate) {
      sender.setUsername(mail.username());
      sender.setPassword(mail.password());
    }
    properties.put("mail.smtp.auth", String.valueOf(authenticate));
    properties.put("mail.smtp.starttls.enable", String.valueOf(mail.starttls()));
    properties.put("mail.smtp.starttls.required", String.valueOf(mail.starttls()));
    properties.put("mail.smtp.connectiontimeout", TIMEOUT_MS);
    properties.put("mail.smtp.timeout", TIMEOUT_MS);
    properties.put("mail.smtp.writetimeout", TIMEOUT_MS);
    return sender;
  }
}

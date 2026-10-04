package si.konferenca.registration.config;

import java.nio.charset.StandardCharsets;
import java.util.Properties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import si.konferenca.registration.application.RegistrationNotifier;
import si.konferenca.registration.infrastructure.MailRegistrationNotifier;

/** SMTP client from the application settings; STARTTLS is required whenever it is on (SB-04). */
@Configuration
public class MailConfig {

  @Bean
  JavaMailSender mailSender(AppProperties properties) {
    AppProperties.Smtp smtp = properties.smtp();
    JavaMailSenderImpl sender = new JavaMailSenderImpl();
    sender.setHost(smtp.host());
    sender.setPort(smtp.port());
    sender.setDefaultEncoding(StandardCharsets.UTF_8.name());
    boolean auth = smtp.username() != null && !smtp.username().isBlank();
    if (auth) {
      sender.setUsername(smtp.username());
      sender.setPassword(smtp.password());
    }
    Properties mail = sender.getJavaMailProperties();
    mail.put("mail.smtp.auth", String.valueOf(auth));
    mail.put("mail.smtp.starttls.enable", String.valueOf(smtp.starttls()));
    mail.put("mail.smtp.starttls.required", String.valueOf(smtp.starttls()));
    mail.put("mail.smtp.connectiontimeout", "5000");
    mail.put("mail.smtp.timeout", "10000");
    mail.put("mail.smtp.writetimeout", "10000");
    return sender;
  }

  @Bean
  RegistrationNotifier registrationNotifier(JavaMailSender sender, AppProperties properties) {
    return new MailRegistrationNotifier(
        sender,
        properties.mailFrom(),
        properties.conferenceName(),
        properties.organizer().emailList());
  }
}

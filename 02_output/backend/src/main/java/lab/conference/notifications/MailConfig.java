package lab.conference.notifications;

import java.util.Properties;
import lab.conference.platform.AppProfile;
import lab.conference.platform.AppProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/** SMTP client: TLS required when enabled (mandatory in production), bounded timeouts. */
@Configuration
public class MailConfig {

  static final int TIMEOUT_MILLIS = 10_000;

  @Bean
  JavaMailSenderImpl mailSender(AppProperties props, AppProfile profile) {
    AppProperties.Mail m = requireSettings(props.mail(), profile);
    JavaMailSenderImpl sender = new JavaMailSenderImpl();
    sender.setHost(m.host());
    sender.setPort(m.port());
    sender.setDefaultEncoding("UTF-8");
    boolean auth = m.username() != null && !m.username().isBlank();
    if (auth) {
      sender.setUsername(m.username());
      sender.setPassword(m.password());
    }
    Properties p = sender.getJavaMailProperties();
    p.put("mail.transport.protocol", "smtp");
    p.put("mail.smtp.auth", Boolean.toString(auth));
    p.put("mail.smtp.starttls.enable", Boolean.toString(m.tlsEnabled()));
    p.put("mail.smtp.starttls.required", Boolean.toString(m.tlsEnabled()));
    p.put("mail.smtp.ssl.checkserveridentity", "true");
    p.put("mail.smtp.connectiontimeout", Integer.toString(TIMEOUT_MILLIS));
    p.put("mail.smtp.timeout", Integer.toString(TIMEOUT_MILLIS));
    p.put("mail.smtp.writetimeout", Integer.toString(TIMEOUT_MILLIS));
    return sender;
  }

  static AppProperties.Mail requireSettings(AppProperties.Mail m, AppProfile profile) {
    if (m == null || isBlank(m.host())) {
      throw new IllegalStateException("SMTP_HOST must be set");
    }
    if (isBlank(m.from())) {
      throw new IllegalStateException("MAIL_FROM must be set");
    }
    if (profile == AppProfile.PRODUCTION && !m.tlsEnabled()) {
      throw new IllegalStateException("SMTP_TLS_ENABLED must be true in production");
    }
    return m;
  }

  private static boolean isBlank(String s) {
    return s == null || s.isBlank();
  }
}

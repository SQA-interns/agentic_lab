package si.konferenca.registration.infrastructure;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import si.konferenca.registration.config.AppProperties;

/**
 * Sends plain-text UTF-8 emails (emails.schema.json, SR-05). Header values come from configuration
 * or validated addresses only.
 */
@Component
public class MailGateway {

  private final ObjectProvider<JavaMailSender> sender;
  private final String from;

  public MailGateway(ObjectProvider<JavaMailSender> sender, AppProperties properties) {
    this.sender = sender;
    this.from = properties.mailFrom();
  }

  /** Sends one message; throws on any failure. */
  public void send(Mail mail) {
    JavaMailSender mailSender = sender.getIfAvailable();
    if (mailSender == null) {
      throw new IllegalStateException("no SMTP host configured");
    }
    MimeMessage message = mailSender.createMimeMessage();
    try {
      MimeMessageHelper helper =
          new MimeMessageHelper(message, mail.attachment() != null, StandardCharsets.UTF_8.name());
      helper.setFrom(from);
      helper.setTo(mail.to().toArray(String[]::new));
      helper.setSubject(mail.subject());
      helper.setText(mail.body(), false);
      if (mail.attachment() != null) {
        helper.addAttachment(
            mail.attachment().fileName(),
            new ByteArrayResource(mail.attachment().content()),
            "application/json");
      }
    } catch (MessagingException e) {
      throw new MailPreparationException(e);
    }
    mailSender.send(message);
  }

  /** An email to send. */
  public record Mail(List<String> to, String subject, String body, Attachment attachment) {

    public Mail {
      to = List.copyOf(to);
    }
  }

  /** A file attached unchanged. */
  public record Attachment(String fileName, byte[] content) {

    public Attachment {
      content = content.clone();
    }

    @Override
    public byte[] content() {
      return content.clone();
    }
  }
}

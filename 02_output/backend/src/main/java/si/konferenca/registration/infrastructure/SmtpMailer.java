package si.konferenca.registration.infrastructure;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import si.konferenca.registration.application.MailDeliveryException;
import si.konferenca.registration.application.Mailer;
import si.konferenca.registration.application.OutgoingMail;

/** SMTP adapter; sends plain-text UTF-8 messages, never HTML (SR-05). */
public class SmtpMailer implements Mailer {

  private final JavaMailSender sender;
  private final String from;

  public SmtpMailer(JavaMailSender sender, String from) {
    this.sender = sender;
    this.from = from;
  }

  @Override
  public void send(OutgoingMail mail) {
    try {
      MimeMessage message = sender.createMimeMessage();
      boolean multipart = mail.attachmentName() != null;
      MimeMessageHelper helper =
          new MimeMessageHelper(message, multipart, StandardCharsets.UTF_8.name());
      helper.setFrom(from);
      helper.setTo(mail.to().toArray(String[]::new));
      helper.setSubject(mail.subject());
      helper.setText(mail.text(), false);
      if (multipart) {
        helper.addAttachment(
            mail.attachmentName(), new ByteArrayResource(mail.attachment()), "application/json");
      }
      sender.send(message);
    } catch (MessagingException | MailException e) {
      throw new MailDeliveryException("mail not accepted", e);
    }
  }
}

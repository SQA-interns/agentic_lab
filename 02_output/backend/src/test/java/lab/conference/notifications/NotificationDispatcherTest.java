package lab.conference.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import lab.conference.platform.AppProfile;
import lab.conference.platform.AppProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

class NotificationDispatcherTest {

  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
  private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
  private final OutboxRepository repo = mock(OutboxRepository.class);
  private final JavaMailSenderImpl sender = mock(JavaMailSenderImpl.class);
  private final AttachmentSource attachments = mock(AttachmentSource.class);
  private final TransactionTemplate tx = mock(TransactionTemplate.class);

  private static AppProperties props(String from) {
    return new AppProperties(
        "test",
        null,
        null,
        null,
        new AppProperties.Mail("h", 25, false, null, null, from, List.of("o@example.test")),
        null,
        null,
        null,
        null,
        null,
        null);
  }

  @SuppressWarnings("unchecked")
  private NotificationDispatcher dispatcher() {
    when(sender.getSession()).thenReturn(Session.getInstance(new Properties()));
    when(tx.execute(any(TransactionCallback.class)))
        .thenAnswer(
            inv -> ((TransactionCallback<Integer>) inv.getArgument(0)).doInTransaction(null));
    return new NotificationDispatcher(
        repo, sender, attachments, tx, clock, props("reg@example.test"));
  }

  private static OutboxEntry entry(NotificationKind kind, String attachment, String sha) {
    return new OutboxEntry(
        new NotificationRequest(
            UUID.randomUUID(), kind, "p@example.test", "Subject", "Body ž", attachment, sha),
        NOW);
  }

  @Test
  void backoffDoublesFromTenSecondsAndIsCapped() {
    assertThat(NotificationDispatcher.backoff(0)).isEqualTo(Duration.ofSeconds(10));
    assertThat(NotificationDispatcher.backoff(1)).isEqualTo(Duration.ofSeconds(10));
    assertThat(NotificationDispatcher.backoff(2)).isEqualTo(Duration.ofSeconds(20));
    assertThat(NotificationDispatcher.backoff(7)).isEqualTo(Duration.ofSeconds(640));
    assertThat(NotificationDispatcher.backoff(8)).isEqualTo(Duration.ofMinutes(15));
    assertThat(NotificationDispatcher.backoff(1000)).isEqualTo(Duration.ofMinutes(15));
  }

  @Test
  void successfulSendMarksEntrySent() throws Exception {
    OutboxEntry e = entry(NotificationKind.PARTICIPANT, null, null);
    when(repo.claimDue(NOW, NotificationDispatcher.BATCH)).thenReturn(List.of(e));
    dispatcher().dispatchDue();
    ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
    verify(sender).send(sent.capture());
    MimeMessage m = sent.getValue();
    m.saveChanges();
    assertThat(m.getRecipients(Message.RecipientType.TO))
        .extracting(Object::toString)
        .containsExactly("p@example.test");
    assertThat(m.getSubject()).isEqualTo("Subject");
    assertThat(m.getMessageID()).startsWith("<" + e.registrationId() + ".participant.");
    assertThat(m.getContentType()).startsWith("text/plain");
    assertThat(e.status()).isEqualTo(OutboxEntry.Status.SENT);
    assertThat(e.attempts()).isEqualTo(1);
  }

  @Test
  void organizerMailCarriesVerifiedAttachment() throws Exception {
    byte[] json = "{\"x\":1}".getBytes();
    OutboxEntry e = entry(NotificationKind.ORGANIZER, "registration-x.json", Hashes.sha256(json));
    when(attachments.load(e.registrationId())).thenReturn(json);
    dispatcher().send(e);
    ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
    verify(sender).send(sent.capture());
    MimeMessage m = sent.getValue();
    m.saveChanges();
    MimeMultipart mp = (MimeMultipart) m.getContent();
    boolean found = false;
    for (int i = 0; i < mp.getCount(); i++) {
      if ("registration-x.json".equals(mp.getBodyPart(i).getFileName())) {
        found = true;
      }
    }
    assertThat(found).isTrue();
  }

  @Test
  void attachmentMismatchIsAFailureAndIsRetried() throws Exception {
    OutboxEntry e = entry(NotificationKind.ORGANIZER, "registration-x.json", "0".repeat(64));
    when(attachments.load(any())).thenReturn("tampered".getBytes());
    when(repo.claimDue(NOW, NotificationDispatcher.BATCH)).thenReturn(List.of(e));
    dispatcher().dispatchDue();
    verify(sender, never()).send(any(MimeMessage.class));
    assertThat(e.status()).isEqualTo(OutboxEntry.Status.PENDING);
    assertThat(e.attempts()).isEqualTo(1);
    assertThat(e.nextAttemptAt()).isEqualTo(NOW.plusSeconds(10));
  }

  @Test
  void smtpFailureSchedulesRetryAndKeepsPending() throws Exception {
    OutboxEntry e = entry(NotificationKind.PARTICIPANT, null, null);
    when(repo.claimDue(NOW, NotificationDispatcher.BATCH)).thenReturn(List.of(e));
    doThrow(new MailSendException("down")).when(sender).send(any(MimeMessage.class));
    NotificationDispatcher d = dispatcher();
    d.dispatchDue();
    d.dispatchDue();
    assertThat(e.status()).isEqualTo(OutboxEntry.Status.PENDING);
    assertThat(e.attempts()).isEqualTo(2);
    assertThat(e.nextAttemptAt()).isEqualTo(NOW.plusSeconds(20));
  }

  @Test
  void databaseFailureDuringDispatchIsContained() {
    NotificationDispatcher d = dispatcher();
    when(repo.claimDue(any(), anyInt()))
        .thenThrow(new org.springframework.dao.QueryTimeoutException("db"));
    d.dispatchDue();
    verify(sender, never()).send(any(MimeMessage.class));
  }

  @Test
  void invalidFromAddressFailsAtStartup() {
    assertThatThrownBy(
            () ->
                new NotificationDispatcher(
                    repo, sender, attachments, tx, clock, props("not valid")))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void mailSettingsAreValidated() {
    AppProperties.Mail ok =
        new AppProperties.Mail("h", 25, false, null, null, "f@example.test", List.of());
    assertThat(MailConfig.requireSettings(ok, AppProfile.LOCAL)).isSameAs(ok);
    assertThatThrownBy(() -> MailConfig.requireSettings(ok, AppProfile.PRODUCTION))
        .hasMessageContaining("TLS");
    assertThatThrownBy(() -> MailConfig.requireSettings(null, AppProfile.LOCAL))
        .hasMessageContaining("SMTP_HOST");
    assertThatThrownBy(
            () ->
                MailConfig.requireSettings(
                    new AppProperties.Mail("h", 25, true, null, null, " ", List.of()),
                    AppProfile.LOCAL))
        .hasMessageContaining("MAIL_FROM");
    JavaMailSenderImpl secure =
        new MailConfig()
            .mailSender(
                new AppProperties(
                    null,
                    null,
                    null,
                    null,
                    new AppProperties.Mail(
                        "smtp.example.test", 587, true, "u", "p", "f@example.test", List.of()),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null),
                AppProfile.PRODUCTION);
    assertThat(secure.getJavaMailProperties())
        .containsEntry("mail.smtp.auth", "true")
        .containsEntry("mail.smtp.starttls.required", "true")
        .containsEntry("mail.smtp.timeout", "10000");
    assertThat(secure.getUsername()).isEqualTo("u");
  }
}

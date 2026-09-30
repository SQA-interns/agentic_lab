package lab.conference.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import lab.conference.platform.AppProfile;
import lab.conference.platform.AppProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

class DispatcherEdgeTest {

  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

  @SuppressWarnings("unchecked")
  @Test
  void fullBatchesAreDrainedAndMessagesAreComplete() throws Exception {
    OutboxRepository repo = mock(OutboxRepository.class);
    JavaMailSenderImpl sender = mock(JavaMailSenderImpl.class);
    TransactionTemplate tx = mock(TransactionTemplate.class);
    when(sender.getSession()).thenReturn(Session.getInstance(new Properties()));
    when(tx.execute(any(TransactionCallback.class)))
        .thenAnswer(
            inv -> ((TransactionCallback<Integer>) inv.getArgument(0)).doInTransaction(null));
    List<OutboxEntry> first = new ArrayList<>();
    for (int i = 0; i < NotificationDispatcher.BATCH; i++) {
      first.add(
          new OutboxEntry(
              new NotificationRequest(
                  UUID.randomUUID(),
                  NotificationKind.PARTICIPANT,
                  "p" + i + "@example.test",
                  "Subject " + i,
                  "Body " + i,
                  null,
                  null),
              NOW));
    }
    OutboxEntry last =
        new OutboxEntry(
            new NotificationRequest(
                UUID.randomUUID(),
                NotificationKind.PARTICIPANT,
                "z@example.test",
                "S",
                "Last body",
                null,
                null),
            NOW);
    when(repo.claimDue(NOW, NotificationDispatcher.BATCH)).thenReturn(first, List.of(last));
    new NotificationDispatcher(
            repo,
            sender,
            mock(AttachmentSource.class),
            tx,
            Clock.fixed(NOW, ZoneOffset.UTC),
            new AppProperties(
                "test",
                null,
                null,
                null,
                new AppProperties.Mail("h", 25, false, null, null, "from@example.test", List.of()),
                null,
                null,
                null,
                null,
                null,
                null))
        .dispatchDue();
    ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
    verify(sender, times(NotificationDispatcher.BATCH + 1)).send(sent.capture());
    MimeMessage m = sent.getAllValues().get(NotificationDispatcher.BATCH);
    m.saveChanges();
    assertThat(m.getFrom()[0].toString()).isEqualTo("from@example.test");
    assertThat((String) m.getContent()).isEqualTo("Last body");
    assertThat(last.status()).isEqualTo(OutboxEntry.Status.SENT);
  }

  @Test
  void mailSenderUsesConfiguredServer() {
    JavaMailSenderImpl s =
        new MailConfig()
            .mailSender(
                new AppProperties(
                    null,
                    null,
                    null,
                    null,
                    new AppProperties.Mail(
                        "smtp.example.test", 2525, false, "", "pw", "f@example.test", List.of()),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null),
                AppProfile.LOCAL);
    assertThat(s.getHost()).isEqualTo("smtp.example.test");
    assertThat(s.getPort()).isEqualTo(2525);
    assertThat(s.getDefaultEncoding()).isEqualTo("UTF-8");
    assertThat(s.getUsername()).isNull();
    assertThat(s.getPassword()).isNull();
    assertThat(s.getJavaMailProperties()).containsEntry("mail.smtp.auth", "false");
    JavaMailSenderImpl auth =
        new MailConfig()
            .mailSender(
                new AppProperties(
                    null,
                    null,
                    null,
                    null,
                    new AppProperties.Mail(
                        "h", 25, true, "user", "secret", "f@example.test", List.of()),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null),
                AppProfile.LOCAL);
    assertThat(auth.getPassword()).isEqualTo("secret");
  }
}

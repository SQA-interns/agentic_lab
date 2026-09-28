package org.example.conference.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.transaction.support.TransactionTemplate;

class OutboxBackoffTest {

  @Test
  void backoffGrowsExponentiallyAndIsCapped() {
    NotificationProperties properties =
        new NotificationProperties(
            "from@example.org",
            List.of("org@example.org"),
            true,
            12,
            10,
            Duration.ofSeconds(30),
            Duration.ofMinutes(30));
    OutboxDispatcher dispatcher =
        new OutboxDispatcher(
            mock(EmailOutboxRepository.class),
            mock(JavaMailSender.class),
            properties,
            mock(TransactionTemplate.class),
            Clock.systemUTC());
    assertThat(dispatcher.backoff(1)).isEqualTo(Duration.ofSeconds(30));
    assertThat(dispatcher.backoff(2)).isEqualTo(Duration.ofSeconds(60));
    assertThat(dispatcher.backoff(4)).isEqualTo(Duration.ofSeconds(240));
    assertThat(dispatcher.backoff(10)).isEqualTo(Duration.ofMinutes(30));
    assertThat(dispatcher.backoff(60)).isEqualTo(Duration.ofMinutes(30));
  }
}

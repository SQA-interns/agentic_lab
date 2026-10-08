package si.konferenca.registration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import si.konferenca.registration.config.StartupChecksTest;
import si.konferenca.registration.infrastructure.JsonCopyStore;
import si.konferenca.registration.persistence.RegistrationStore;

class RetentionServiceTest {

  private final RegistrationStore store = mock(RegistrationStore.class);
  private final JsonCopyStore copies = mock(JsonCopyStore.class);
  private final PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);
  private final Instant now = Instant.parse("2026-10-08T03:00:00Z");
  private RetentionService service;

  @BeforeEach
  void setUp() {
    when(txManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    service =
        new RetentionService(
            store,
            copies,
            new TransactionTemplate(txManager),
            StartupChecksTest.validProduction(),
            Clock.fixed(now, ZoneOffset.UTC));
  }

  @Test
  @DisplayName("D-16 the cutoff is exactly RETENTION_DAYS before now")
  void cutoffIsRetentionDaysBeforeNow() {
    service.purge();
    verify(store).idsReceivedBefore(Instant.parse("2025-10-08T03:00:00Z"));
  }

  @Test
  @DisplayName("D-16 expired rows and their copies are deleted")
  void deletesRowsAndCopies() throws Exception {
    UUID a = UUID.randomUUID();
    UUID b = UUID.randomUUID();
    when(store.idsReceivedBefore(any())).thenReturn(List.of(a, b));

    assertThat(service.purge()).isEqualTo(2);
    verify(store).delete(a);
    verify(store).delete(b);
    verify(copies).delete(a);
    verify(copies).delete(b);
  }

  @Test
  void oneFailureDoesNotStopTheOthers() throws Exception {
    UUID a = UUID.randomUUID();
    UUID b = UUID.randomUUID();
    when(store.idsReceivedBefore(any())).thenReturn(List.of(a, b));
    doThrow(new IOException("locked")).when(copies).delete(a);

    assertThat(service.purge()).isEqualTo(1);
    verify(store).delete(b);
    verify(copies).delete(b);
  }

  @Test
  void rowDeleteFailureKeepsTheCopy() throws Exception {
    UUID a = UUID.randomUUID();
    when(store.idsReceivedBefore(any())).thenReturn(List.of(a));
    doThrow(new IllegalStateException("db")).when(store).delete(a);

    assertThat(service.purge()).isZero();
    verify(copies, never()).delete(a);
  }

  @Test
  void startupAndScheduleBothPurge() {
    service.atStartup();
    service.scheduled();
    verify(store, org.mockito.Mockito.times(2)).idsReceivedBefore(any());
  }
}

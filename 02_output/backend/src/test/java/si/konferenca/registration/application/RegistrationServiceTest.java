package si.konferenca.registration.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import si.konferenca.registration.domain.Registration;
import si.konferenca.registration.domain.RegistrationRepository;

class RegistrationServiceTest {

  private static final Instant NOW = Instant.parse("2026-09-30T10:15:30.123456Z");
  private static final byte[] JSON = "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8);

  private final CaptchaVerifier captcha = mock(CaptchaVerifier.class);
  private final RegistrationRepository repository = mock(RegistrationRepository.class);
  private final JsonCopyStore copies = mock(JsonCopyStore.class);
  private final NotificationSender notifications = mock(NotificationSender.class);
  private final PlatformTransactionManager tm = mock(PlatformTransactionManager.class);
  private final TransactionStatus status = new SimpleTransactionStatus();
  private RegistrationService service;

  @BeforeEach
  void setUp() {
    when(tm.getTransaction(any())).thenReturn(status);
    when(captcha.verify("token", "10.0.0.1")).thenReturn(true);
    when(copies.write(any())).thenReturn(JSON);
    service =
        new RegistrationService(
            new RegistrationValidator(RegistrationValidatorTest.CATALOG),
            captcha,
            repository,
            copies,
            notifications,
            tm,
            Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void storesThenWritesCopyThenCommitsThenSendsEmails() {
    RegistrationService.Accepted accepted =
        service.register(RegistrationValidatorTest.external(), "10.0.0.1");

    InOrder order = inOrder(repository, copies, tm, notifications);
    order.verify(repository).existsByEmailIgnoreCase("ana@example.si");
    order.verify(repository).saveAndFlush(any(Registration.class));
    order.verify(copies).write(any());
    order.verify(tm).commit(status);
    order.verify(notifications).sendParticipantConfirmation(accepted.copy());
    order.verify(notifications).sendOrganizerNotification(accepted.copy(), JSON);
    assertThat(accepted.json()).isSameAs(JSON);
  }

  @Test
  void buildsTheCopyFromTheValidatedData() {
    RegistrationCopy copy =
        service.register(RegistrationValidatorTest.external(), "10.0.0.1").copy();

    assertThat(copy.schemaVersion()).isEqualTo(1);
    assertThat(copy.submittedAt()).isEqualTo(Instant.parse("2026-09-30T10:15:30.123Z"));
    assertThat(copy.type()).isEqualTo("EXTERNAL");
    assertThat(copy.firstName()).isEqualTo("Ana");
    assertThat(copy.options())
        .extracting(RegistrationCopy.Option::id, RegistrationCopy.Option::category)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("meal", "meal"),
            org.assertj.core.groups.Tuple.tuple("ws", "workshop"));
    assertThat(copy.consents())
        .extracting(RegistrationCopy.Consent::id)
        .containsExactly("privacy", "news");
    assertThat(copy.consents()).allMatch(c -> c.givenAt().equals(copy.submittedAt()));
  }

  @Test
  void rejectsFailedCaptchaBeforeAnyStorage() {
    when(captcha.verify("token", "10.0.0.1")).thenReturn(false);

    assertThatThrownBy(() -> service.register(RegistrationValidatorTest.external(), "10.0.0.1"))
        .isInstanceOf(ValidationException.class)
        .extracting(e -> ((ValidationException) e).violations().get(0).field())
        .isEqualTo("recaptchaToken");
    verify(repository, never()).saveAndFlush(any());
    verify(copies, never()).write(any());
  }

  @Test
  void invalidInputNeverReachesCaptcha() {
    RegistrationCommand invalid =
        new RegistrationCommand(null, null, null, null, null, null, null, null, null, null, "t");

    assertThatThrownBy(() -> service.register(invalid, "10.0.0.1"))
        .isInstanceOf(ValidationException.class);
    verify(captcha, never()).verify(any(), any());
  }

  @Test
  void rejectsDuplicateEmailBeforeStorage() {
    when(repository.existsByEmailIgnoreCase("ana@example.si")).thenReturn(true);

    assertThatThrownBy(() -> service.register(RegistrationValidatorTest.external(), "10.0.0.1"))
        .isInstanceOf(DuplicateRegistrationException.class);
    verify(repository, never()).saveAndFlush(any());
  }

  @Test
  void concurrentDuplicateFromUniqueIndexIsADuplicateAndRemovesTheCopy() {
    when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("unique"));

    assertThatThrownBy(() -> service.register(RegistrationValidatorTest.external(), "10.0.0.1"))
        .isInstanceOf(DuplicateRegistrationException.class);
    verify(copies).delete(any(UUID.class));
    verify(tm).rollback(status);
    verify(notifications, never()).sendParticipantConfirmation(any());
  }

  @Test
  void failedCopyRollsBackAndSendsNothing() {
    StorageException failure = new StorageException("disk", new java.io.IOException("x"));
    when(copies.write(any())).thenThrow(failure);

    assertThatThrownBy(() -> service.register(RegistrationValidatorTest.external(), "10.0.0.1"))
        .isSameAs(failure);
    verify(tm).rollback(status);
    verify(tm, never()).commit(any());
    ArgumentCaptor<UUID> id = ArgumentCaptor.forClass(UUID.class);
    verify(copies).delete(id.capture());
    assertThat(id.getValue()).isNotNull();
    verify(notifications, never()).sendParticipantConfirmation(any());
    verify(notifications, never()).sendOrganizerNotification(any(), any());
  }

  @Test
  void failedCommitRemovesTheWrittenCopy() {
    doThrow(new org.springframework.transaction.TransactionSystemException("commit"))
        .when(tm)
        .commit(status);

    assertThatThrownBy(() -> service.register(RegistrationValidatorTest.external(), "10.0.0.1"))
        .isInstanceOf(org.springframework.transaction.TransactionSystemException.class);
    verify(copies).delete(any(UUID.class));
  }

  @Test
  void emailFailuresDoNotUndoTheRegistration() {
    doThrow(new IllegalStateException("smtp down"))
        .when(notifications)
        .sendParticipantConfirmation(any());
    doThrow(new IllegalStateException("smtp down"))
        .when(notifications)
        .sendOrganizerNotification(any(), any());

    RegistrationService.Accepted accepted =
        service.register(RegistrationValidatorTest.external(), "10.0.0.1");

    assertThat(accepted.copy().id()).isNotNull();
    verify(notifications).sendOrganizerNotification(eq(accepted.copy()), any());
    verify(copies, never()).delete(any());
  }
}

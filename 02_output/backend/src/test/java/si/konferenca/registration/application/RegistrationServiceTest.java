package si.konferenca.registration.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import si.konferenca.registration.domain.Registration;
import si.konferenca.registration.domain.RegistrationRepository;
import si.konferenca.registration.domain.RegistrationType;
import tools.jackson.databind.json.JsonMapper;

class RegistrationServiceTest {

  private static final Instant NOW = Instant.parse("2026-10-05T10:00:00.123456Z");

  private final RegistrationRepository repository = mock(RegistrationRepository.class);
  private final JsonCopyStore copies = mock(JsonCopyStore.class);
  private final CaptchaVerifier captcha = mock(CaptchaVerifier.class);
  private final RegistrationNotifier notifier = mock(RegistrationNotifier.class);
  private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
  private final Path written = Path.of("copy.json");
  private RegistrationService service;

  @BeforeEach
  void setUp() {
    TransactionStatus status = new SimpleTransactionStatus();
    when(transactions.getTransaction(any())).thenReturn(status);
    when(captcha.verify(anyString(), any())).thenReturn(true);
    when(copies.write(any(), any(), any())).thenReturn(written);
    service =
        new RegistrationService(
            repository,
            copies,
            captcha,
            () -> RegistrationValidatorTest.OPTIONS,
            new RegistrationJson(JsonMapper.builder().build()),
            notifier,
            new TransactionTemplate(transactions),
            Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private static RegistrationCommand command() {
    return RegistrationValidatorTest.external("Ana", "Ana@Example.si", "IJS");
  }

  @Test
  void storesThenNotifiesAndTruncatesTheTimeToMillis() {
    RegistrationService.Accepted accepted = service.register(command(), "10.0.0.1");

    assertThat(accepted.type()).isEqualTo(RegistrationType.EXTERNAL);
    assertThat(accepted.acceptedAt()).isEqualTo(Instant.parse("2026-10-05T10:00:00.123Z"));
    ArgumentCaptor<Registration> saved = ArgumentCaptor.forClass(Registration.class);
    verify(repository).saveAndFlush(saved.capture());
    assertThat(saved.getValue().id()).isEqualTo(accepted.id());
    assertThat(saved.getValue().consentId()).isEqualTo("c1");
    verify(copies).write(eq(accepted.id()), eq(accepted.acceptedAt()), any());
    verify(transactions).commit(any());
    verify(notifier).registrationAccepted(eq(saved.getValue()), any());
    verify(captcha).verify("token", "10.0.0.1");
    verify(repository).existsByEmailNormalized("ana@example.si");
  }

  @Test
  void failedCaptchaStoresNothing() {
    when(captcha.verify(anyString(), any())).thenReturn(false);

    assertThatThrownBy(() -> service.register(command(), null))
        .isInstanceOfSatisfying(
            RegistrationRejectedException.class,
            e ->
                assertThat(e.errors())
                    .containsExactly(new FieldError("captchaToken", "CAPTCHA_FAILED")));
    verify(repository, never()).saveAndFlush(any());
    verify(notifier, never()).registrationAccepted(any(), any());
  }

  @Test
  void invalidCommandNeverReachesCaptcha() {
    var invalid = RegistrationValidatorTest.external("", "a@b.si", "x");

    assertThatThrownBy(() -> service.register(invalid, null))
        .isInstanceOf(RegistrationRejectedException.class);
    verify(captcha, never()).verify(any(), any());
  }

  @Test
  void knownEmailIsRejectedBeforeStorage() {
    when(repository.existsByEmailNormalized("ana@example.si")).thenReturn(true);

    assertThatThrownBy(() -> service.register(command(), null))
        .isInstanceOf(EmailAlreadyRegisteredException.class);
    verify(repository, never()).saveAndFlush(any());
  }

  @Test
  void concurrentDuplicateFromTheUniqueConstraintIsADuplicate() {
    when(repository.saveAndFlush(any()))
        .thenThrow(
            new DataIntegrityViolationException(
                "x",
                new SQLException(
                    "duplicate key value violates unique constraint"
                        + " \"registration_email_normalized_uk\"")));

    assertThatThrownBy(() -> service.register(command(), null))
        .isInstanceOf(EmailAlreadyRegisteredException.class);
    verify(copies, never()).write(any(), any(), any());
    verify(notifier, never()).registrationAccepted(any(), any());
  }

  @Test
  void otherIntegrityViolationIsStorageFailure() {
    when(repository.saveAndFlush(any()))
        .thenThrow(new DataIntegrityViolationException("x", new SQLException("check failed")));

    assertThatThrownBy(() -> service.register(command(), null))
        .isInstanceOf(StorageUnavailableException.class);
  }

  @Test
  void copyFailureRollsBackAndStoresNothing() {
    when(copies.write(any(), any(), any()))
        .thenThrow(new UncheckedIOException(new java.io.IOException("disk")));

    assertThatThrownBy(() -> service.register(command(), null))
        .isInstanceOf(StorageUnavailableException.class);
    verify(transactions).rollback(any());
    verify(copies, never()).delete(any());
    verify(notifier, never()).registrationAccepted(any(), any());
  }

  @Test
  void commitFailureDeletesTheWrittenCopy() {
    doThrow(new TransactionSystemException("commit failed")).when(transactions).commit(any());

    assertThatThrownBy(() -> service.register(command(), null))
        .isInstanceOf(StorageUnavailableException.class);
    verify(copies).delete(written);
    verify(notifier, never()).registrationAccepted(any(), any());
  }

  @Test
  void integrityViolationAfterTheCopyDeletesTheCopy() {
    doThrow(new DataIntegrityViolationException("late", new SQLException("deferred")))
        .when(transactions)
        .commit(any());

    assertThatThrownBy(() -> service.register(command(), null))
        .isInstanceOf(StorageUnavailableException.class);
    verify(copies).delete(written);
  }

  @Test
  void notifierFailureDoesNotChangeTheOutcome() {
    doThrow(new IllegalStateException("smtp")).when(notifier).registrationAccepted(any(), any());

    RegistrationService.Accepted accepted = service.register(command(), null);

    assertThat(accepted.id()).isInstanceOf(UUID.class);
    verify(copies, never()).delete(any());
  }
}

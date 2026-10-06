package si.konferenca.registration.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import si.konferenca.registration.domain.Registration;

class RegistrationServiceTest {

  /** Transaction manager that can be told to fail the commit. */
  static final class FakeTransactions extends AbstractPlatformTransactionManager {
    private static final long serialVersionUID = 1L;
    boolean failCommit;
    int rollbacks;

    @Override
    protected Object doGetTransaction() {
      return new Object();
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {}

    @Override
    protected void doCommit(DefaultTransactionStatus status) {
      if (failCommit) {
        throw new TransactionSystemException("commit failed");
      }
    }

    @Override
    protected void doRollback(DefaultTransactionStatus status) {
      rollbacks++;
    }
  }

  private final CaptchaVerifier captcha = mock(CaptchaVerifier.class);
  private final RegistrationStore store = mock(RegistrationStore.class);
  private final JsonCopyStore copies = mock(JsonCopyStore.class);
  private final Notifier notifier = mock(Notifier.class);
  private final FakeTransactions tx = new FakeTransactions();
  private RegistrationService service;

  @BeforeEach
  void setUp() {
    service =
        new RegistrationService(
            new RegistrationValidator(Fixtures.CATALOG),
            captcha,
            store,
            copies,
            notifier,
            new TransactionTemplate(tx),
            Clock.fixed(Instant.parse("2026-10-06T18:00:00.123456Z"), ZoneOffset.UTC));
    when(captcha.verify(anyString(), any())).thenReturn(true);
    when(copies.serialize(any())).thenReturn("{}".getBytes());
    when(copies.fileNameFor(any())).thenReturn("copy.json");
  }

  @Test
  void storesRowThenCopyThenNotifies() {
    Registration r = service.register(Fixtures.external(), "10.0.0.1");

    InOrder order = inOrder(store, copies, notifier);
    order.verify(store).emailExists("ana@example.si");
    order.verify(store).insert(r, "copy.json");
    order.verify(copies).write(eq(r), any());
    order.verify(notifier).registrationAccepted(eq(r), any());
    assertThat(r.receivedAt()).isEqualTo(Instant.parse("2026-10-06T18:00:00.123Z"));
    verify(captcha).verify("token", "10.0.0.1");
  }

  @Test
  void failedCaptchaIsAFieldErrorAndStoresNothing() {
    when(captcha.verify(anyString(), any())).thenReturn(false);

    assertThatThrownBy(() -> service.register(Fixtures.external(), null))
        .isInstanceOfSatisfying(
            ValidationException.class,
            e ->
                assertThat(e.errors())
                    .containsExactly(new FieldError("recaptchaToken", "RECAPTCHA_FAILED")));
    verify(store, never()).insert(any(), any());
    verify(notifier, never()).registrationAccepted(any(), any());
  }

  @Test
  void invalidCommandDoesNotCallCaptcha() {
    RegistrationCommand c = Fixtures.student();
    RegistrationCommand invalid =
        new RegistrationCommand(
            c.type(),
            "",
            c.lastName(),
            c.email(),
            null,
            c.studyInstitution(),
            c.studyProgramme(),
            c.studentId(),
            c.optionIds(),
            true,
            "token");

    assertThatThrownBy(() -> service.register(invalid, null))
        .isInstanceOf(ValidationException.class);
    verify(captcha, never()).verify(any(), any());
  }

  @Test
  void existingEmailIsDuplicateAndStoresNothing() {
    when(store.emailExists("ana@example.si")).thenReturn(true);

    assertThatThrownBy(() -> service.register(Fixtures.external(), null))
        .isInstanceOf(DuplicateEmailException.class);
    verify(store, never()).insert(any(), any());
    verify(copies, never()).write(any(), any());
    assertThat(tx.rollbacks).isEqualTo(1);
  }

  @Test
  void copyFailureRollsBackAndLeavesNoFileToDelete() {
    doThrow(new UncheckedIOException(new java.io.IOException("disk")))
        .when(copies)
        .write(any(), any());

    assertThatThrownBy(() -> service.register(Fixtures.external(), null))
        .isInstanceOf(ServiceUnavailableException.class);
    assertThat(tx.rollbacks).isEqualTo(1);
    verify(copies, never()).delete(any());
    verify(notifier, never()).registrationAccepted(any(), any());
  }

  @Test
  void commitFailureAfterCopyRemovesTheCopy() {
    tx.failCommit = true;

    assertThatThrownBy(() -> service.register(Fixtures.external(), null))
        .isInstanceOf(ServiceUnavailableException.class);
    verify(copies).delete(any());
    verify(notifier, never()).registrationAccepted(any(), any());
  }

  @Test
  void concurrentDuplicateDetectedByConstraintIsDuplicate() {
    doThrow(new DataIntegrityViolationException("uq_registration_email"))
        .when(store)
        .insert(any(), any());
    when(store.emailExists("ana@example.si")).thenReturn(false, true);

    assertThatThrownBy(() -> service.register(Fixtures.external(), null))
        .isInstanceOf(DuplicateEmailException.class);
  }

  @Test
  void otherConstraintViolationIsUnavailable() {
    doThrow(new DataIntegrityViolationException("ck")).when(store).insert(any(), any());

    assertThatThrownBy(() -> service.register(Fixtures.external(), null))
        .isInstanceOf(ServiceUnavailableException.class);
  }

  @Test
  void duplicateRecheckFailureIsUnavailable() {
    doThrow(new DataIntegrityViolationException("x")).when(store).insert(any(), any());
    when(store.emailExists("ana@example.si"))
        .thenReturn(false)
        .thenThrow(new IllegalStateException("db down"));

    assertThatThrownBy(() -> service.register(Fixtures.external(), null))
        .isInstanceOf(ServiceUnavailableException.class);
  }
}

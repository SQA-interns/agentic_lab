package si.konferenca.registration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import si.konferenca.registration.domain.FieldError;
import si.konferenca.registration.domain.Registration;
import si.konferenca.registration.domain.RegistrationType;
import si.konferenca.registration.infrastructure.JsonCopyStore;
import si.konferenca.registration.infrastructure.RecaptchaVerifier;
import si.konferenca.registration.persistence.RegistrationStore;

class RegistrationServiceTest {

  private final OptionCatalogueProvider catalogue = mock(OptionCatalogueProvider.class);
  private final RecaptchaVerifier recaptcha = mock(RecaptchaVerifier.class);
  private final RegistrationStore store = mock(RegistrationStore.class);
  private final JsonCopyStore copies = mock(JsonCopyStore.class);
  private final PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);
  private final NotificationService notifications = mock(NotificationService.class);
  private final Clock clock =
      Clock.fixed(Instant.parse("2026-10-08T10:00:00.123456Z"), ZoneOffset.UTC);
  private RegistrationService service;

  @BeforeEach
  void setUp() {
    when(catalogue.catalogue()).thenReturn(RegistrationValidatorTest.CATALOGUE);
    when(recaptcha.verify(anyString(), any())).thenReturn(true);
    TransactionStatus status = new SimpleTransactionStatus();
    when(txManager.getTransaction(any())).thenReturn(status);
    service =
        new RegistrationService(
            catalogue,
            recaptcha,
            store,
            copies,
            new TransactionTemplate(txManager),
            notifications,
            clock);
  }

  @Test
  @DisplayName("AR-05 accepted only after row and copy are written; then notified")
  void acceptedRegistrationIsStoredCopiedCommittedAndNotified() throws Exception {
    Registration r = service.register(RegistrationValidatorTest.external(), "10.0.0.1");

    assertThat(r.type()).isEqualTo(RegistrationType.EXTERNAL);
    assertThat(r.receivedAt()).isEqualTo(Instant.parse("2026-10-08T10:00:00.123Z"));
    assertThat(r.consents()).allSatisfy(c -> assertThat(c.givenAt()).isEqualTo(r.receivedAt()));
    assertThat(r.options()).extracting(o -> o.name()).containsExactly("A");
    var order = org.mockito.Mockito.inOrder(store, copies, txManager, notifications);
    order.verify(store).insert(r);
    order.verify(copies).write(r);
    order.verify(txManager).commit(any());
    order.verify(notifications).registrationAccepted(r);
    verify(recaptcha).verify("t", "10.0.0.1");
  }

  @Test
  void invalidRegistrationSpendsNoTokenAndStoresNothing() throws Exception {
    RegistrationCommand bad =
        new RegistrationCommand(
            "EXTERNAL",
            "",
            "B",
            "a@b.si",
            "Org",
            null,
            null,
            null,
            null,
            java.util.List.of("data"),
            "t");

    assertThatThrownBy(() -> service.register(bad, "ip"))
        .isInstanceOf(RegistrationRejectedException.class)
        .satisfies(
            e ->
                assertThat(((RegistrationRejectedException) e).errors())
                    .containsExactly(new FieldError("firstName", "required")));
    verify(recaptcha, never()).verify(anyString(), any());
    verify(store, never()).insert(any());
    verify(copies, never()).write(any());
  }

  @Test
  @DisplayName("SR-01 a rejected token stores nothing")
  void failedTokenIsRejected() throws Exception {
    when(recaptcha.verify(anyString(), any())).thenReturn(false);

    assertThatThrownBy(() -> service.register(RegistrationValidatorTest.external(), "ip"))
        .isInstanceOf(RegistrationRejectedException.class)
        .satisfies(
            e ->
                assertThat(((RegistrationRejectedException) e).errors())
                    .containsExactly(new FieldError("recaptchaToken", "captcha_failed")));
    verify(store, never()).insert(any());
  }

  @Test
  @DisplayName("D-15 an already registered email is a duplicate before anything is written")
  void duplicateDetectedByLookup() throws Exception {
    when(store.emailRegistered("ana@example.si")).thenReturn(true);

    assertThatThrownBy(() -> service.register(RegistrationValidatorTest.external(), "ip"))
        .isInstanceOf(DuplicateEmailException.class);
    verify(store, never()).insert(any());
    verify(copies, never()).write(any());
    verify(txManager).rollback(any());
    verify(notifications, never()).registrationAccepted(any());
  }

  @Test
  @DisplayName("D-15 a concurrent duplicate surfaces as the unique-constraint violation")
  void duplicateDetectedByConstraint() throws Exception {
    doThrow(
            new DataIntegrityViolationException(
                "x", new SQLException("violates unique constraint \"uq_registration_email\"")))
        .when(store)
        .insert(any());

    assertThatThrownBy(() -> service.register(RegistrationValidatorTest.external(), "ip"))
        .isInstanceOf(DuplicateEmailException.class);
    verify(copies, never()).write(any());
  }

  @Test
  void otherConstraintViolationIsAStorageFailure() throws Exception {
    doThrow(
            new DataIntegrityViolationException(
                "x", new SQLException("ck_registration_type_fields")))
        .when(store)
        .insert(any());

    assertThatThrownBy(() -> service.register(RegistrationValidatorTest.external(), "ip"))
        .isInstanceOf(StorageFailureException.class);
  }

  @Test
  @DisplayName("AC-005-03 a failed copy write rolls back and is not accepted")
  void copyWriteFailureRollsBack() throws Exception {
    doThrow(new IOException("disk full")).when(copies).write(any());

    assertThatThrownBy(() -> service.register(RegistrationValidatorTest.external(), "ip"))
        .isInstanceOf(StorageFailureException.class);
    verify(txManager).rollback(any());
    verify(txManager, never()).commit(any());
    verify(notifications, never()).registrationAccepted(any());
  }

  @Test
  @DisplayName("AC-005-03 a failed commit removes the copy already written")
  void commitFailureRemovesCopy() throws Exception {
    doThrow(new CannotCreateTransactionException("db gone")).when(txManager).commit(any());

    assertThatThrownBy(() -> service.register(RegistrationValidatorTest.external(), "ip"))
        .isInstanceOf(StorageFailureException.class);
    ArgumentCaptor<java.util.UUID> id = ArgumentCaptor.forClass(java.util.UUID.class);
    verify(copies).delete(id.capture());
    verify(copies).write(org.mockito.ArgumentMatchers.argThat(r -> r.id().equals(id.getValue())));
    verify(notifications, never()).registrationAccepted(any());
  }

  @Test
  void copyCleanupFailureStillReportsStorageFailure() throws Exception {
    doThrow(new CannotCreateTransactionException("db gone")).when(txManager).commit(any());
    when(copies.delete(any())).thenThrow(new IOException("locked"));

    assertThatThrownBy(() -> service.register(RegistrationValidatorTest.external(), "ip"))
        .isInstanceOf(StorageFailureException.class);
  }
}

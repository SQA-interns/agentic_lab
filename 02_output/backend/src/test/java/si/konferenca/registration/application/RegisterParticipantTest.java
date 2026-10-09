package si.konferenca.registration.application;

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
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import si.konferenca.registration.domain.ErrorCode;
import si.konferenca.registration.domain.Field;
import si.konferenca.registration.domain.Fixtures;
import si.konferenca.registration.domain.Registration;
import si.konferenca.registration.domain.RegistrationValidator;
import si.konferenca.registration.domain.Submission;
import si.konferenca.registration.domain.ValidationError;

class RegisterParticipantTest {

  private final CaptchaVerifier captcha = mock(CaptchaVerifier.class);
  private final RegistrationStore store = mock(RegistrationStore.class);
  private final RegistrationTransaction transaction = mock(RegistrationTransaction.class);
  private final RegisterParticipant useCase =
      new RegisterParticipant(
          new RegistrationValidator(Fixtures.catalogue(), Fixtures.CLOCK),
          captcha,
          store,
          transaction);

  private static Submission valid() {
    return Fixtures.external(Fixtures.externalValues(), List.of("ws-a"));
  }

  @Test
  void ac00102_validSubmissionIsStored() {
    when(captcha.verify("tok", "10.0.0.1")).thenReturn(true);

    RegistrationResult result = useCase.register(valid(), "tok", "10.0.0.1");

    assertThat(result).isInstanceOf(RegistrationResult.Accepted.class);
    verify(transaction).store(any(Registration.class));
  }

  @Test
  void invalidFieldsAreReportedWithoutSpendingTheCaptchaToken() {
    Map<Field, String> values = Fixtures.externalValues();
    values.put(Field.EMAIL, "nope");

    RegistrationResult result =
        useCase.register(Fixtures.external(values, List.of()), "tok", "10.0.0.1");

    assertThat(result).isInstanceOf(RegistrationResult.Rejected.class);
    verify(captcha, never()).verify(anyString(), anyString());
    verify(transaction, never()).store(any());
  }

  @Test
  void ac00116_failedOrMissingCaptchaIsRejected() {
    when(captcha.verify(anyString(), any())).thenReturn(false);

    for (String token : new String[] {"bad", "", " ", null}) {
      RegistrationResult result = useCase.register(valid(), token, "10.0.0.1");
      assertThat(((RegistrationResult.Rejected) result).errors())
          .containsExactly(ValidationError.of("recaptchaToken", ErrorCode.CAPTCHA_FAILED));
    }
    verify(transaction, never()).store(any());
  }

  @Test
  void ac00115_existingEmailIsADuplicate() {
    when(captcha.verify(anyString(), any())).thenReturn(true);
    when(store.existsByNormalizedEmail("ana@example.si")).thenReturn(true);

    RegistrationResult.Rejected result =
        (RegistrationResult.Rejected) useCase.register(valid(), "tok", null);

    assertThat(result.duplicate()).isTrue();
    assertThat(result.errors())
        .containsExactly(ValidationError.of("email", ErrorCode.ALREADY_REGISTERED));
    verify(transaction, never()).store(any());
  }

  @Test
  void d18_concurrentDuplicateDetectedByTheDatabaseIsADuplicate() {
    when(captcha.verify(anyString(), any())).thenReturn(true);
    doThrow(new DuplicateEmailException(null)).when(transaction).store(any());

    RegistrationResult result = useCase.register(valid(), "tok", null);

    assertThat(((RegistrationResult.Rejected) result).duplicate()).isTrue();
  }

  @Test
  void ac00503_storageFailurePropagates() {
    when(captcha.verify(anyString(), any())).thenReturn(true);
    doThrow(new StorageException("disk", new IOException())).when(transaction).store(any());

    assertThatThrownBy(() -> useCase.register(valid(), "tok", null))
        .isInstanceOf(StorageException.class);
  }

  @Test
  void ar05_transactionWritesRowThenCopyThenPublishes() {
    RegistrationStore rows = mock(RegistrationStore.class);
    JsonCopyStore copies = mock(JsonCopyStore.class);
    org.springframework.context.ApplicationEventPublisher events =
        mock(org.springframework.context.ApplicationEventPublisher.class);
    Registration registration =
        Fixtures.registration(si.konferenca.registration.domain.RegistrationType.EXTERNAL);
    when(copies.write(registration)).thenReturn(new byte[] {1, 2});
    var order = org.mockito.Mockito.inOrder(rows, copies, events);

    new RegistrationTransaction(rows, copies, events).store(registration);

    order.verify(rows).insert(registration);
    order.verify(copies).write(registration);
    order.verify(events).publishEvent(any(RegistrationAccepted.class));
  }

  @Test
  void ar05_copyFailureStopsBeforeTheEvent() {
    RegistrationStore rows = mock(RegistrationStore.class);
    JsonCopyStore copies = mock(JsonCopyStore.class);
    org.springframework.context.ApplicationEventPublisher events =
        mock(org.springframework.context.ApplicationEventPublisher.class);
    Registration registration =
        Fixtures.registration(si.konferenca.registration.domain.RegistrationType.STUDENT);
    when(copies.write(registration)).thenThrow(new StorageException("x", new IOException()));

    assertThatThrownBy(() -> new RegistrationTransaction(rows, copies, events).store(registration))
        .isInstanceOf(StorageException.class);
    verify(events, never()).publishEvent(any());
  }
}

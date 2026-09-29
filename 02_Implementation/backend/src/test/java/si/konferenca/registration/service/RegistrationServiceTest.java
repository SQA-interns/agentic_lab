package si.konferenca.registration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import jakarta.mail.MessagingException;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.TransactionTemplate;
import si.konferenca.registration.domain.ConferenceOption;
import si.konferenca.registration.domain.OptionCategory;
import si.konferenca.registration.domain.Registration;
import si.konferenca.registration.domain.RegistrationSnapshot;
import si.konferenca.registration.integration.JsonBackupStore;
import si.konferenca.registration.integration.MailNotifier;
import si.konferenca.registration.integration.RecaptchaVerifier;
import si.konferenca.registration.persistence.ConferenceOptionRepository;
import si.konferenca.registration.persistence.RegistrationRepository;
import si.konferenca.registration.service.RegistrationExceptions.RecaptchaFailedException;
import si.konferenca.registration.service.RegistrationExceptions.RegistrationNotSavedException;
import si.konferenca.registration.service.RegistrationExceptions.ValidationFailedException;

class RegistrationServiceTest {

  private final RecaptchaVerifier recaptcha = mock(RecaptchaVerifier.class);
  private final OptionCatalogService catalog = mock(OptionCatalogService.class);
  private final RegistrationRepository registrations = mock(RegistrationRepository.class);
  private final ConferenceOptionRepository options = mock(ConferenceOptionRepository.class);
  private final JsonBackupStore backups = mock(JsonBackupStore.class);
  private final MailNotifier mail = mock(MailNotifier.class);
  private final PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);
  private final ConferenceOption workshop =
      new ConferenceOption("ws", OptionCategory.WORKSHOP, "Workshop", true, 0);
  private RegistrationService service;

  private static final RegistrationCommand VALID =
      new RegistrationCommand(
          "EXTERNAL",
          "Ana",
          "Novak",
          "a@x.si",
          "IJS",
          null,
          null,
          null,
          List.of("ws"),
          true,
          "tok");

  @BeforeEach
  void setUp() throws IOException {
    service =
        new RegistrationService(
            recaptcha,
            new RegistrationValidator(),
            catalog,
            registrations,
            options,
            backups,
            mail,
            new TransactionTemplate(txManager),
            Clock.fixed(Instant.parse("2026-09-29T10:00:00.123456789Z"), ZoneOffset.UTC));
    when(recaptcha.verify(anyString(), any())).thenReturn(true);
    when(catalog.catalog()).thenReturn(Map.of("ws", workshop));
    when(options.getReferenceById("ws")).thenReturn(workshop);
    when(registrations.saveAndFlush(any(Registration.class))).thenAnswer(inv -> inv.getArgument(0));
    when(backups.serialize(any())).thenReturn(new byte[] {'{', '}'});
  }

  @Test
  void acceptedRegistrationIsStoredBackedUpAndMailed() throws Exception {
    RegistrationSnapshot result = service.register(VALID, "1.2.3.4");

    assertThat(result.submittedAt()).isEqualTo(Instant.parse("2026-09-29T10:00:00.123456Z"));
    assertThat(result.options())
        .extracting(RegistrationSnapshot.SnapshotOption::id)
        .containsExactly("ws");
    verify(backups).write(eq(result.registrationId()), any());
    verify(mail).sendParticipantConfirmation(result);
    ArgumentCaptor<byte[]> attached = ArgumentCaptor.forClass(byte[].class);
    verify(mail).sendOrganizerNotification(eq(result), attached.capture());
    assertThat(attached.getValue()).containsExactly((byte) '{', (byte) '}');
  }

  @Test
  void failedRecaptchaStopsBeforeValidationAndStorage() {
    when(recaptcha.verify(anyString(), any())).thenReturn(false);

    assertThatThrownBy(() -> service.register(VALID, "1.2.3.4"))
        .isInstanceOf(RecaptchaFailedException.class);
    verifyNoInteractions(catalog, registrations, backups, mail);
  }

  @Test
  void invalidSubmissionStoresNothing() {
    RegistrationCommand invalid =
        new RegistrationCommand(
            "EXTERNAL", "", "Novak", "a@x.si", "IJS", null, null, null, null, true, "tok");

    assertThatThrownBy(() -> service.register(invalid, null))
        .isInstanceOf(ValidationFailedException.class);
    verifyNoInteractions(registrations, backups, mail);
  }

  @Test
  void backupFailureRollsBackAndRemovesThePartialBackup() throws Exception {
    doThrow(new IOException("disk full")).when(backups).write(any(), any());

    assertThatThrownBy(() -> service.register(VALID, null))
        .isInstanceOf(RegistrationNotSavedException.class);
    verify(txManager).rollback(any());
    verify(backups).deleteQuietly(any(UUID.class));
    verifyNoInteractions(mail);
  }

  @Test
  void commitFailureDeletesTheAlreadyWrittenBackup() throws Exception {
    doThrow(new TransactionSystemException("commit failed")).when(txManager).commit(any());

    assertThatThrownBy(() -> service.register(VALID, null))
        .isInstanceOf(RegistrationNotSavedException.class);
    ArgumentCaptor<UUID> written = ArgumentCaptor.forClass(UUID.class);
    verify(backups).write(written.capture(), any());
    verify(backups).deleteQuietly(written.getValue());
    verifyNoInteractions(mail);
  }

  @Test
  void mailFailuresDoNotUndoTheRegistration() throws Exception {
    doThrow(new MessagingException("smtp down")).when(mail).sendParticipantConfirmation(any());
    doThrow(new IllegalStateException("smtp down"))
        .when(mail)
        .sendOrganizerNotification(any(), any());

    RegistrationSnapshot result = service.register(VALID, null);

    assertThat(result.registrationId()).isNotNull();
    verify(backups, never()).deleteQuietly(any());
    verify(mail).sendOrganizerNotification(eq(result), any());
  }
}

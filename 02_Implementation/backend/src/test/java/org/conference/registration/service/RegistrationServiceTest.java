package org.conference.registration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.conference.registration.domain.ConferenceOption;
import org.conference.registration.domain.OptionCategory;
import org.conference.registration.domain.ParticipantDetails;
import org.conference.registration.domain.Registration;
import org.conference.registration.domain.RegistrationType;
import org.conference.registration.repository.RegistrationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class RegistrationServiceTest {

  private final RegistrationRepository repository = mock(RegistrationRepository.class);
  private final OptionCatalog catalog = mock(OptionCatalog.class);
  private final FormTokenService tokens = mock(FormTokenService.class);
  private final BackupWriter backupWriter = mock(BackupWriter.class);
  private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
  private final MutableClock clock =
      new MutableClock(Instant.parse("2026-05-01T10:00:00.123456789Z"));
  private final RegistrationService service =
      new RegistrationService(
          repository,
          catalog,
          tokens,
          backupWriter,
          new ObjectMapper().registerModule(new JavaTimeModule()),
          events,
          clock);
  private final FormTokenService.VerifiedToken verified =
      new FormTokenService.VerifiedToken("nonce", 0);

  @BeforeEach
  void setUp() {
    TransactionSynchronizationManager.initSynchronization();
    when(tokens.verify("token")).thenReturn(verified);
  }

  @AfterEach
  void tearDown() {
    TransactionSynchronizationManager.clearSynchronization();
  }

  private static RegistrationCommand command(boolean consent, String honeypot) {
    return new RegistrationCommand(
        RegistrationType.EXTERNAL,
        ParticipantDetails.external("Ana", "Novak", "ana@example.si", "FERI"),
        consent,
        List.of("ws-a"),
        "token",
        honeypot);
  }

  @Test
  void storesRegistrationWritesBackupConsumesTokenAndPublishesEvent() {
    ConferenceOption ws =
        new ConferenceOption(
            "ws-a", OptionCategory.WORKSHOP, "Workshop A", true, Set.of(RegistrationType.EXTERNAL));
    when(catalog.resolve(RegistrationType.EXTERNAL, List.of("ws-a"))).thenReturn(List.of(ws));

    RegistrationResult result = service.register(command(true, ""));

    ArgumentCaptor<Registration> saved = ArgumentCaptor.forClass(Registration.class);
    verify(repository).saveAndFlush(saved.capture());
    assertThat(saved.getValue().getId()).isEqualTo(result.id());
    assertThat(saved.getValue().getOptions()).hasSize(1);
    assertThat(result.createdAt()).isEqualTo(Instant.parse("2026-05-01T10:00:00.123456Z"));

    ArgumentCaptor<byte[]> json = ArgumentCaptor.forClass(byte[].class);
    verify(backupWriter)
        .write(org.mockito.ArgumentMatchers.eq(saved.getValue().getBackupFile()), json.capture());
    assertThat(new String(json.getValue(), java.nio.charset.StandardCharsets.UTF_8))
        .contains("\"schemaVersion\"")
        .contains("ws-a")
        .doesNotContain("studentId");
    verify(tokens).consume(verified);
    verify(events).publishEvent(any(RegistrationCompletedEvent.class));
  }

  @Test
  void filledHoneypotIsRejectedBeforeAnythingIsStored() {
    assertThatThrownBy(() -> service.register(command(true, "http://spam")))
        .isInstanceOf(SubmissionRejectedException.class);
    verifyNoInteractions(repository, backupWriter, events);
  }

  @Test
  void missingConsentIsRejected() {
    assertThatThrownBy(() -> service.register(command(false, null)))
        .isInstanceOf(RegistrationValidationException.class)
        .extracting("field")
        .isEqualTo("privacyConsent");
    verifyNoInteractions(repository, backupWriter);
  }

  @Test
  void invalidTokenIsRejected() {
    when(tokens.verify("token")).thenThrow(new SubmissionRejectedException("bad"));
    assertThatThrownBy(() -> service.register(command(true, null)))
        .isInstanceOf(SubmissionRejectedException.class);
    verifyNoInteractions(repository);
  }

  @Test
  void backupFailurePropagatesAndSuppressesEvent() {
    when(catalog.resolve(any(), any())).thenReturn(List.of());
    doThrow(new BackupFailedException("disk full", null))
        .when(backupWriter)
        .write(anyString(), any());
    assertThatThrownBy(() -> service.register(command(true, null)))
        .isInstanceOf(BackupFailedException.class);
    verify(tokens, never()).consume(any());
    verify(events, never()).publishEvent(any());
  }
}

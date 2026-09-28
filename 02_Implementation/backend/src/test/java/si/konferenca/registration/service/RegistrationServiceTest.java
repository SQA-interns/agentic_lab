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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import si.konferenca.registration.domain.ConferenceOption;
import si.konferenca.registration.domain.OptionCategory;
import si.konferenca.registration.domain.Registration;
import si.konferenca.registration.domain.RegistrationRepository;
import si.konferenca.registration.domain.RegistrationType;

class RegistrationServiceTest {

  private static final Instant NOW = Instant.parse("2026-05-04T08:15:30.123456789Z");

  private final RegistrationRepository repository = mock(RegistrationRepository.class);
  private final ConferenceOptionCatalog catalog = mock(ConferenceOptionCatalog.class);
  private final CaptchaVerifier captcha = mock(CaptchaVerifier.class);
  private final RegistrationBackupStore backupStore = mock(RegistrationBackupStore.class);
  private final RegistrationNotifier notifier = mock(RegistrationNotifier.class);
  private final TestTransactionManager transactionManager = new TestTransactionManager();
  private RegistrationService service;

  @BeforeEach
  void setUp() {
    ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    service =
        new RegistrationService(
            repository,
            catalog,
            captcha,
            backupStore,
            notifier,
            new RegistrationJsonSerializer(mapper),
            new TransactionTemplate(transactionManager),
            Clock.fixed(NOW, ZoneOffset.UTC));
    when(catalog.findById(anyString())).thenReturn(Optional.empty());
    when(catalog.findById("ws-1"))
        .thenReturn(
            Optional.of(new ConferenceOption("ws-1", "Workshop 1", OptionCategory.WORKSHOP, true)));
    when(catalog.findById("ws-old"))
        .thenReturn(
            Optional.of(new ConferenceOption("ws-old", "Old", OptionCategory.WORKSHOP, false)));
    when(captcha.verify(eq("ok"), any())).thenReturn(true);
  }

  private static NewRegistration external(List<String> optionIds, Map<String, Boolean> consents) {
    return new NewRegistration(
        RegistrationType.EXTERNAL,
        "Ana",
        "Novak",
        "ana@example.si",
        "IJS",
        null,
        null,
        null,
        optionIds,
        consents,
        "ok",
        "192.0.2.1");
  }

  private static NewRegistration student() {
    return new NewRegistration(
        RegistrationType.STUDENT,
        "Žiga",
        "Čeh",
        "ziga@example.si",
        null,
        "UL",
        "RI",
        "6321",
        List.of(),
        Map.of("privacy", true),
        "ok",
        null);
  }

  @Test
  void registersExternalParticipant() {
    RegistrationResult result =
        service.register(external(List.of("ws-1"), Map.of("privacy", true)));

    ArgumentCaptor<Registration> saved = ArgumentCaptor.forClass(Registration.class);
    verify(repository).saveAndFlush(saved.capture());
    Registration registration = saved.getValue();
    assertThat(registration.getType()).isEqualTo(RegistrationType.EXTERNAL);
    assertThat(registration.getOrganization()).isEqualTo("IJS");
    assertThat(registration.getStudentId()).isNull();
    assertThat(registration.getOptions())
        .extracting(o -> o.getOptionName())
        .containsExactly("Workshop 1");
    assertThat(registration.getCreatedAt()).isEqualTo(Instant.parse("2026-05-04T08:15:30.123456Z"));
    assertThat(result.registrationId()).isEqualTo(registration.getId());
    assertThat(result.type()).isEqualTo(RegistrationType.EXTERNAL);
    verify(backupStore).store(eq(registration.getId()), eq(registration.getCreatedAt()), any());
    verify(notifier).sendParticipantConfirmation(any());
    verify(notifier).sendOrganizerNotification(any(), any());
    assertThat(transactionManager.commits).isEqualTo(1);
  }

  @Test
  void registersStudentWithStudentFieldsOnly() {
    service.register(student());

    ArgumentCaptor<Registration> saved = ArgumentCaptor.forClass(Registration.class);
    verify(repository).saveAndFlush(saved.capture());
    assertThat(saved.getValue().getType()).isEqualTo(RegistrationType.STUDENT);
    assertThat(saved.getValue().getStudyInstitution()).isEqualTo("UL");
    assertThat(saved.getValue().getStudyProgramme()).isEqualTo("RI");
    assertThat(saved.getValue().getStudentId()).isEqualTo("6321");
    assertThat(saved.getValue().getOrganization()).isNull();
  }

  @Test
  void backupJsonContainsRegistrationData() {
    service.register(external(List.of("ws-1"), Map.of("privacy", true)));

    ArgumentCaptor<byte[]> json = ArgumentCaptor.forClass(byte[].class);
    verify(backupStore).store(any(), any(), json.capture());
    String text = new String(json.getValue(), java.nio.charset.StandardCharsets.UTF_8);
    assertThat(text)
        .contains("\"registrationType\" : \"EXTERNAL\"")
        .contains("\"organization\" : \"IJS\"")
        .contains("\"id\" : \"ws-1\"")
        .contains("\"privacy\" : true")
        .doesNotContain("studentId")
        .doesNotContain("captcha");
  }

  @Test
  void rejectsMissingMandatoryConsent() {
    assertThatThrownBy(() -> service.register(external(List.of(), Map.of())))
        .isInstanceOf(RegistrationValidationException.class)
        .satisfies(
            e ->
                assertThat(((RegistrationValidationException) e).getViolations())
                    .containsExactly(new FieldViolation("consents.privacy", "CONSENT_REQUIRED")));
    verifyNoInteractions(repository, backupStore, notifier, captcha);
  }

  @Test
  void rejectsFalseConsent() {
    assertThatThrownBy(() -> service.register(external(List.of(), Map.of("privacy", false))))
        .isInstanceOf(RegistrationValidationException.class);
  }

  @Test
  void rejectsUnknownAndInactiveOptionsTogether() {
    assertThatThrownBy(
            () ->
                service.register(
                    external(List.of("nope", "ws-old", "ws-1"), Map.of("privacy", true))))
        .isInstanceOf(RegistrationValidationException.class)
        .satisfies(
            e ->
                assertThat(((RegistrationValidationException) e).getViolations())
                    .containsExactly(
                        new FieldViolation("optionIds", "UNKNOWN_OPTION"),
                        new FieldViolation("optionIds", "INACTIVE_OPTION")));
    verify(repository, never()).saveAndFlush(any());
  }

  @Test
  void rejectsNullOptionId() {
    java.util.ArrayList<String> ids = new java.util.ArrayList<>();
    ids.add(null);
    assertThatThrownBy(() -> service.register(external(ids, Map.of("privacy", true))))
        .isInstanceOf(RegistrationValidationException.class);
  }

  @Test
  void rejectsInvalidCaptchaBeforePersisting() {
    NewRegistration command =
        new NewRegistration(
            RegistrationType.EXTERNAL,
            "Ana",
            "Novak",
            "ana@example.si",
            "IJS",
            null,
            null,
            null,
            List.of(),
            Map.of("privacy", true),
            "bad",
            null);

    assertThatThrownBy(() -> service.register(command))
        .isInstanceOf(CaptchaVerificationException.class);
    verifyNoInteractions(repository, backupStore, notifier);
  }

  @Test
  void rejectsBlankCaptchaWithoutCallingVerifier() {
    NewRegistration command =
        new NewRegistration(
            RegistrationType.EXTERNAL,
            "Ana",
            "Novak",
            "ana@example.si",
            "IJS",
            null,
            null,
            null,
            List.of(),
            Map.of("privacy", true),
            " ",
            null);

    assertThatThrownBy(() -> service.register(command))
        .isInstanceOf(CaptchaVerificationException.class);
    verifyNoInteractions(captcha);
  }

  @Test
  void backupFailureRollsBackAndSendsNoEmail() {
    doThrow(new IllegalStateException("disk"))
        .when(backupStore)
        .store(any(UUID.class), any(Instant.class), any(byte[].class));

    assertThatThrownBy(() -> service.register(student())).isInstanceOf(IllegalStateException.class);

    assertThat(transactionManager.rollbacks).isEqualTo(1);
    assertThat(transactionManager.commits).isZero();
    verifyNoInteractions(notifier);
  }

  @Test
  void commitFailureDeletesBackupAndSendsNoEmail() {
    transactionManager.failCommit = true;

    assertThatThrownBy(() -> service.register(student()))
        .isInstanceOf(TransactionSystemException.class);

    verify(backupStore).delete(any(UUID.class), any(Instant.class));
    verifyNoInteractions(notifier);
  }

  @Test
  void emailFailuresDoNotFailTheRegistration() {
    doThrow(new RuntimeException("smtp")).when(notifier).sendParticipantConfirmation(any());
    doThrow(new RuntimeException("smtp")).when(notifier).sendOrganizerNotification(any(), any());

    RegistrationResult result = service.register(student());

    assertThat(result.registrationId()).isNotNull();
    verify(notifier).sendOrganizerNotification(any(), any());
  }

  @Test
  void duplicateOptionIdsAreCollapsed() {
    service.register(external(List.of("ws-1", "ws-1"), Map.of("privacy", true)));

    ArgumentCaptor<Registration> saved = ArgumentCaptor.forClass(Registration.class);
    verify(repository).saveAndFlush(saved.capture());
    assertThat(saved.getValue().getOptions()).hasSize(1);
  }

  /** Minimal transaction manager that supports synchronizations and can fail on commit. */
  private static final class TestTransactionManager extends AbstractPlatformTransactionManager {

    private static final long serialVersionUID = 1L;

    int commits;
    int rollbacks;
    boolean failCommit;

    @Override
    protected Object doGetTransaction() {
      return new Object();
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
      // nothing to begin
    }

    @Override
    protected void doCommit(DefaultTransactionStatus status) {
      if (failCommit) {
        throw new TransactionSystemException("commit failed");
      }
      commits++;
    }

    @Override
    protected void doRollback(DefaultTransactionStatus status) {
      rollbacks++;
    }
  }
}

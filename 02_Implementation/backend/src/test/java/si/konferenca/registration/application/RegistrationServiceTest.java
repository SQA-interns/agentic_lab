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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import si.konferenca.registration.TestFixtures;
import si.konferenca.registration.application.port.CaptchaVerifier;
import si.konferenca.registration.application.port.RegistrationBackup;
import si.konferenca.registration.application.port.RegistrationNotifier;
import si.konferenca.registration.domain.OptionCategory;
import si.konferenca.registration.domain.Registration;
import si.konferenca.registration.domain.RegistrationType;
import si.konferenca.registration.persistence.RegistrationRepository;

class RegistrationServiceTest {

  private static final Instant NOW = Instant.parse("2026-05-01T12:00:00Z");

  private ValidatorFactory validatorFactory;
  private CaptchaVerifier captcha;
  private RegistrationRepository repository;
  private RegistrationBackup backup;
  private RegistrationNotifier notifier;
  private PlatformTransactionManager transactionManager;
  private TransactionStatus status;
  private RegistrationService service;

  @BeforeEach
  void setUp() {
    validatorFactory = Validation.buildDefaultValidatorFactory();
    ConferenceCatalog catalog =
        new ConferenceCatalog(TestFixtures.properties(TestFixtures.conference()));
    captcha = mock(CaptchaVerifier.class);
    repository = mock(RegistrationRepository.class);
    backup = mock(RegistrationBackup.class);
    notifier = mock(RegistrationNotifier.class);
    transactionManager = mock(PlatformTransactionManager.class);
    status = new SimpleTransactionStatus();
    when(transactionManager.getTransaction(any())).thenReturn(status);
    when(repository.saveAndFlush(any()))
        .thenAnswer(
            invocation -> {
              Registration registration = invocation.getArgument(0);
              ReflectionTestUtils.setField(registration, "id", UUID.randomUUID());
              return registration;
            });
    ObjectMapper objectMapper =
        JsonMapper.builder()
            .findAndAddModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();
    service =
        new RegistrationService(
            new RegistrationValidator(validatorFactory.getValidator(), catalog),
            captcha,
            catalog,
            repository,
            backup,
            notifier,
            new RegistrationJsonMapper(objectMapper),
            transactionManager,
            Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @AfterEach
  void tearDown() {
    validatorFactory.close();
  }

  private static RegistrationCommand validCommand() {
    return new RegistrationCommand(
        RegistrationType.EXTERNAL,
        " Janez ",
        "Novak",
        "janez@example.si",
        "ACME",
        null,
        null,
        null,
        List.of("ws-a", "meal-lunch"),
        List.of("privacy"),
        "token");
  }

  @Test
  void storesInDatabaseThenBackupThenNotifies() throws IOException {
    Registration result = service.register(validCommand(), "10.0.0.1");

    InOrder order = inOrder(captcha, repository, backup, transactionManager, notifier);
    order.verify(captcha).verify("token", "10.0.0.1");
    order.verify(repository).saveAndFlush(any());
    order.verify(backup).write(eq(result), anyString());
    order.verify(transactionManager).commit(status);
    order.verify(notifier).registrationAccepted(eq(result), anyString());

    assertThat(result.getFirstName()).isEqualTo("Janez");
    assertThat(result.getCreatedAt()).isEqualTo(NOW);
    assertThat(result.getSelectedOptions())
        .extracting(o -> o.getCategory())
        .containsExactly(OptionCategory.WORKSHOP, OptionCategory.MEAL);
    assertThat(result.getConsentIds()).containsExactly("privacy");
  }

  @Test
  void backupAndEmailReceiveTheSameRawJson() throws IOException {
    service.register(validCommand(), "10.0.0.1");
    ArgumentCaptor<String> backupJson = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> mailJson = ArgumentCaptor.forClass(String.class);
    verify(backup).write(any(), backupJson.capture());
    verify(notifier).registrationAccepted(any(), mailJson.capture());
    assertThat(backupJson.getValue()).isEqualTo(mailJson.getValue());
    assertThat(backupJson.getValue())
        .contains("\"firstName\" : \"Janez\"")
        .contains("\"type\" : \"EXTERNAL\"")
        .contains("\"createdAt\" : \"2026-05-01T12:00:00Z\"")
        .doesNotContain("token");
  }

  @Test
  void invalidInputStoresNothingAndSendsNothing() {
    RegistrationCommand invalid =
        new RegistrationCommand(
            RegistrationType.EXTERNAL,
            "",
            "Novak",
            "bad",
            "ACME",
            null,
            null,
            null,
            List.of(),
            List.of("privacy"),
            "token");
    assertThatThrownBy(() -> service.register(invalid, "10.0.0.1"))
        .isInstanceOf(ValidationException.class);
    verifyNoInteractions(captcha, repository, backup, notifier);
  }

  @Test
  void failedCaptchaStoresNothing() {
    doThrow(new CaptchaFailedException()).when(captcha).verify(any(), any());
    assertThatThrownBy(() -> service.register(validCommand(), "10.0.0.1"))
        .isInstanceOf(CaptchaFailedException.class);
    verifyNoInteractions(repository, backup, notifier);
  }

  @Test
  void backupFailureRollsBackAndDoesNotNotify() throws IOException {
    doThrow(new IOException("disk full")).when(backup).write(any(), anyString());
    assertThatThrownBy(() -> service.register(validCommand(), "10.0.0.1"))
        .isInstanceOf(RegistrationStorageException.class);
    verify(transactionManager).rollback(status);
    verify(transactionManager, never()).commit(any());
    verifyNoInteractions(notifier);
  }

  @Test
  void databaseFailureDoesNotWriteBackupOrNotify() {
    doThrow(new DataIntegrityViolationException("x")).when(repository).saveAndFlush(any());
    assertThatThrownBy(() -> service.register(validCommand(), "10.0.0.1"))
        .isInstanceOf(RegistrationStorageException.class);
    verifyNoInteractions(backup, notifier);
  }
}

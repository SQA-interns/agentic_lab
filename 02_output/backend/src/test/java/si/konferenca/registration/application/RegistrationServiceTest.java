package si.konferenca.registration.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import si.konferenca.registration.domain.Category;
import si.konferenca.registration.domain.ConferenceOption;
import si.konferenca.registration.domain.ConsentDefinition;
import si.konferenca.registration.domain.OptionCatalogue;
import si.konferenca.registration.domain.Registration;
import si.konferenca.registration.domain.RegistrationType;

class RegistrationServiceTest {

  private static final Instant NOW = Instant.parse("2026-10-09T08:15:30.123456Z");

  private final List<String> events = new ArrayList<>();
  private final PlatformTransactionManager transactionManager =
      mock(PlatformTransactionManager.class);
  private FakeRepository repository;
  private FakeCopyStore copyStore;
  private FakeNotifier notifier;
  private CaptchaVerifier captcha;
  private RegistrationService service;

  @BeforeEach
  void setUp() {
    when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    repository = new FakeRepository();
    copyStore = new FakeCopyStore();
    notifier = new FakeNotifier();
    captcha =
        (token, ip) -> {
          events.add("captcha " + token + " " + ip);
          if (!"ok".equals(token)) {
            throw new CaptchaVerifier.CaptchaFailedException();
          }
        };
    OptionCatalogue catalogue =
        new OptionCatalogue(
            Map.of(),
            List.of(
                new ConferenceOption(
                    "ws-a",
                    "Workshop A",
                    Category.WORKSHOP,
                    true,
                    EnumSet.allOf(RegistrationType.class))),
            List.of(new ConsentDefinition("data", "I agree.", true)));
    service =
        new RegistrationService(
            new RegistrationValidator(catalogue),
            (token, ip) -> captcha.verify(token, ip),
            repository,
            copyStore,
            notifier,
            new TransactionTemplate(transactionManager),
            Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private static RegistrationCommand command(String email, String token) {
    return new RegistrationCommand(
        RegistrationType.EXTERNAL,
        "Ana",
        "Novak",
        email,
        "IJS",
        null,
        null,
        null,
        List.of("ws-a"),
        List.of("data"),
        token);
  }

  @Test
  void acceptedRegistrationIsStoredCopiedThenNotified() {
    Registration registration = service.register(command("Ana@Example.si", "ok"), "10.0.0.1");

    assertThat(events)
        .containsExactly(
            "captcha ok 10.0.0.1",
            "exists ana@example.si",
            "insert",
            "copy",
            "participant",
            "organizers " + registration.id());
    assertThat(registration.registeredAt()).isEqualTo(Instant.parse("2026-10-09T08:15:30.123Z"));
    assertThat(registration.consents().get(0).givenAt()).isEqualTo(registration.registeredAt());
    assertThat(registration.consents().get(0).consentText()).isEqualTo("I agree.");
    assertThat(registration.options().get(0).optionName()).isEqualTo("Workshop A");
    assertThat(notifier.organizerCopy).isEqualTo(copyStore.lastCopy);
  }

  @Test
  void invalidRegistrationIsRejectedBeforeAntiAutomation() {
    assertThatThrownBy(() -> service.register(command("bad", "ok"), "ip"))
        .isInstanceOf(ValidationException.class);

    assertThat(events).isEmpty();
  }

  @Test
  void rejectedTokenStopsBeforeStorage() {
    assertThatThrownBy(() -> service.register(command("ana@example.si", "no"), "ip"))
        .isInstanceOf(CaptchaVerifier.CaptchaFailedException.class);

    assertThat(events).containsExactly("captcha no ip");
  }

  @Test
  void knownEmailIsADuplicate() {
    repository.existing = true;

    assertThatThrownBy(() -> service.register(command("ana@example.si", "ok"), "ip"))
        .isInstanceOf(DuplicateEmailException.class);

    assertThat(events).doesNotContain("insert");
  }

  @Test
  void copyFailureRollsBackAndSendsNothing() {
    copyStore.fail = true;

    assertThatThrownBy(() -> service.register(command("ana@example.si", "ok"), "ip"))
        .isInstanceOf(RegistrationCopyStore.CopyStoreException.class);

    assertThat(events).containsSubsequence("insert", "copy").doesNotContain("participant");
    org.mockito.Mockito.verify(transactionManager).rollback(any());
    assertThat(copyStore.deleted).isEmpty();
  }

  @Test
  void commitFailureAfterTheCopyRemovesTheCopy() {
    doThrow(new TransactionSystemException("commit failed")).when(transactionManager).commit(any());

    assertThatThrownBy(() -> service.register(command("ana@example.si", "ok"), "ip"))
        .isInstanceOf(TransactionSystemException.class);

    assertThat(copyStore.deleted).hasSize(1);
    assertThat(events).doesNotContain("participant");
  }

  @Test
  void emailFailuresDoNotUndoTheRegistration() {
    notifier.fail = true;

    Registration registration = service.register(command("ana@example.si", "ok"), "ip");

    assertThat(registration).isNotNull();
    assertThat(events).contains("participant", "organizers " + registration.id());
    assertThat(copyStore.deleted).isEmpty();
  }

  private final class FakeRepository implements RegistrationRepository {
    boolean existing;

    @Override
    public boolean existsByEmailNormalized(String emailNormalized) {
      events.add("exists " + emailNormalized);
      return existing;
    }

    @Override
    public void insert(Registration registration) {
      events.add("insert");
    }

    @Override
    public List<Registration> findAllOldestFirst() {
      return List.of();
    }
  }

  private final class FakeCopyStore implements RegistrationCopyStore {
    boolean fail;
    byte[] lastCopy;
    final List<UUID> deleted = new ArrayList<>();

    @Override
    public byte[] write(Registration registration) {
      events.add("copy");
      if (fail) {
        throw new CopyStoreException("disk full", null);
      }
      lastCopy = registration.id().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
      return lastCopy;
    }

    @Override
    public void delete(UUID registrationId) {
      deleted.add(registrationId);
    }
  }

  private final class FakeNotifier implements RegistrationNotifier {
    boolean fail;
    byte[] organizerCopy;

    @Override
    public void notifyParticipant(Registration registration) {
      events.add("participant");
      if (fail) {
        throw new IllegalStateException("smtp down");
      }
    }

    @Override
    public void notifyOrganizers(Registration registration, byte[] jsonCopy) {
      events.add("organizers " + registration.id());
      organizerCopy = jsonCopy;
      if (fail) {
        throw new IllegalStateException("smtp down");
      }
    }
  }
}

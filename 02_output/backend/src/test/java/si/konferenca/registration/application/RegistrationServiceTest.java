package si.konferenca.registration.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import si.konferenca.registration.application.RegistrationPorts.CaptchaResult;
import si.konferenca.registration.application.RegistrationPorts.DuplicateEmailException;
import si.konferenca.registration.domain.ConferenceCatalog;
import si.konferenca.registration.domain.ConferenceOption;
import si.konferenca.registration.domain.ConsentDefinition;
import si.konferenca.registration.domain.OptionCategory;
import si.konferenca.registration.domain.Registration;
import si.konferenca.registration.domain.RegistrationSubmission;
import si.konferenca.registration.domain.RegistrationType;
import si.konferenca.registration.domain.RegistrationValidator;

class RegistrationServiceTest {

  private static final UUID ID = UUID.fromString("3f1c2a9e-5b7d-4c1e-9a2b-8d6e4f0a1b2c");
  private static final Instant NOW = Instant.parse("2026-10-06T08:00:00.123456Z");

  private final List<String> events = new ArrayList<>();
  private CaptchaResult captcha;
  private boolean emailExists;
  private RuntimeException insertFailure;
  private RuntimeException copyFailure;
  private RuntimeException commitFailure;
  private Registration inserted;
  private Registration notified;
  private byte[] notifiedCopy;
  private RegistrationService service;

  @BeforeEach
  void setUp() {
    captcha = CaptchaResult.PASSED;
    ConferenceCatalog catalog =
        new ConferenceCatalog(
            List.of(
                new ConferenceOption(
                    "ws",
                    "Workshop",
                    OptionCategory.WORKSHOP,
                    true,
                    EnumSet.allOf(RegistrationType.class))),
            List.of(new ConsentDefinition("data", "I agree", true)));
    RegistrationPorts.RegistrationRepository repository =
        new RegistrationPorts.RegistrationRepository() {
          @Override
          public boolean existsByNormalizedEmail(String normalizedEmail) {
            events.add("exists " + normalizedEmail);
            return emailExists;
          }

          @Override
          public void insert(Registration registration) {
            events.add("insert");
            if (insertFailure != null) {
              throw insertFailure;
            }
            inserted = registration;
          }

          @Override
          public List<Registration> findAll() {
            return List.of();
          }
        };
    RegistrationPorts.JsonCopyStore copies =
        new RegistrationPorts.JsonCopyStore() {
          @Override
          public byte[] write(Registration registration) {
            events.add("write copy");
            if (copyFailure != null) {
              throw copyFailure;
            }
            return new byte[] {1, 2, 3};
          }

          @Override
          public void delete(UUID registrationId) {
            events.add("delete copy " + registrationId);
          }
        };
    RegistrationPorts.Transactions transactions =
        work -> {
          events.add("begin");
          work.run();
          if (commitFailure != null) {
            events.add("rollback");
            throw commitFailure;
          }
          events.add("commit");
        };
    service =
        new RegistrationService(
            new RegistrationValidator(catalog),
            token -> {
              events.add("verify " + token);
              return captcha;
            },
            repository,
            copies,
            transactions,
            (registration, copy) -> {
              events.add("notify");
              notified = registration;
              notifiedCopy = copy;
            },
            Clock.fixed(NOW, ZoneOffset.UTC),
            () -> ID);
  }

  private static RegistrationSubmission submission() {
    return new RegistrationSubmission(
        "external",
        "Ana",
        "Novak",
        " Ana@Example.com ",
        "IJS",
        null,
        null,
        null,
        List.of("ws"),
        List.of("data"),
        "token");
  }

  @Test
  void acceptedRegistrationIsStoredThenNotifiedInThisOrder() {
    RegistrationResult result = service.register(submission());

    assertThat(result).isInstanceOf(RegistrationResult.Accepted.class);
    assertThat(events)
        .containsExactly(
            "verify token",
            "exists ana@example.com",
            "begin",
            "insert",
            "write copy",
            "commit",
            "notify");
    Registration registration = ((RegistrationResult.Accepted) result).registration();
    assertThat(registration.id()).isEqualTo(ID);
    assertThat(registration.receivedAt()).isEqualTo(Instant.parse("2026-10-06T08:00:00.123Z"));
    assertThat(registration.consents())
        .singleElement()
        .satisfies(
            c -> {
              assertThat(c.id()).isEqualTo("data");
              assertThat(c.text()).isEqualTo("I agree");
              assertThat(c.givenAt()).isEqualTo(registration.receivedAt());
            });
    assertThat(registration.options())
        .containsExactly(
            new Registration.SelectedOption("ws", "Workshop", OptionCategory.WORKSHOP));
    assertThat(inserted).isEqualTo(registration);
    assertThat(notified).isEqualTo(registration);
    assertThat(notifiedCopy).containsExactly(1, 2, 3);
  }

  @Test
  void invalidSubmissionIsRejectedBeforeAnyVerificationOrStorage() {
    RegistrationSubmission invalid =
        new RegistrationSubmission(
            "external",
            "",
            "Novak",
            "a@b.si",
            "IJS",
            null,
            null,
            null,
            List.of(),
            List.of("data"),
            "t");

    RegistrationResult result = service.register(invalid);

    assertThat(result).isInstanceOf(RegistrationResult.Invalid.class);
    assertThat(((RegistrationResult.Invalid) result).errors()).hasSize(1);
    assertThat(events).isEmpty();
  }

  @Test
  void failedTokenStopsBeforeStorage() {
    captcha = CaptchaResult.FAILED;

    assertThat(service.register(submission()))
        .isInstanceOf(RegistrationResult.CaptchaRejected.class);
    assertThat(events).containsExactly("verify token");
  }

  @Test
  void unavailableVerificationStopsBeforeStorage() {
    captcha = CaptchaResult.UNAVAILABLE;

    assertThat(service.register(submission()))
        .isInstanceOf(RegistrationResult.CaptchaUnavailable.class);
    assertThat(events).containsExactly("verify token");
  }

  @Test
  void knownEmailIsReportedWithoutStoring() {
    emailExists = true;

    assertThat(service.register(submission()))
        .isInstanceOf(RegistrationResult.DuplicateEmail.class);
    assertThat(events).containsExactly("verify token", "exists ana@example.com");
  }

  @Test
  void concurrentDuplicateDetectedOnInsertIsReportedWithoutNotification() {
    insertFailure = new DuplicateEmailException();

    assertThat(service.register(submission()))
        .isInstanceOf(RegistrationResult.DuplicateEmail.class);
    assertThat(events).doesNotContain("write copy", "notify").doesNotContain("delete copy " + ID);
  }

  @Test
  void duplicateFoundAtCommitRemovesTheWrittenCopy() {
    commitFailure = new DuplicateEmailException();

    assertThat(service.register(submission()))
        .isInstanceOf(RegistrationResult.DuplicateEmail.class);
    assertThat(events).contains("delete copy " + ID).doesNotContain("notify");
  }

  @Test
  void commitFailureAfterTheCopyWasWrittenRemovesItAndFails() {
    commitFailure = new IllegalStateException("commit failed");

    assertThatThrownBy(() -> service.register(submission()))
        .isInstanceOf(StorageFailedException.class)
        .hasCause(commitFailure)
        .satisfies(e -> assertThat(((StorageFailedException) e).registrationId()).isEqualTo(ID));
    assertThat(events).endsWith("rollback", "delete copy " + ID).doesNotContain("notify");
  }

  @Test
  void copyFailureFailsWithoutDeletingAnything() {
    copyFailure = new IllegalStateException("disk full");

    assertThatThrownBy(() -> service.register(submission()))
        .isInstanceOf(StorageFailedException.class);
    assertThat(events).noneMatch(e -> e.startsWith("delete copy")).doesNotContain("notify");
  }

  @Test
  void insertFailureFailsWithoutWritingTheCopy() {
    insertFailure = new IllegalStateException("database down");

    assertThatThrownBy(() -> service.register(submission()))
        .isInstanceOf(StorageFailedException.class);
    assertThat(events).doesNotContain("write copy", "notify");
  }

  @Test
  void exportWritesAllRegistrations() {
    List<Registration> all = List.of();
    ExportService export =
        new ExportService(
            new RegistrationPorts.RegistrationRepository() {
              @Override
              public boolean existsByNormalizedEmail(String normalizedEmail) {
                return false;
              }

              @Override
              public void insert(Registration registration) {}

              @Override
              public List<Registration> findAll() {
                return all;
              }
            },
            registrations -> {
              assertThat(registrations).isSameAs(all);
              return new byte[] {7};
            });

    assertThat(export.export()).containsExactly(7);
  }
}

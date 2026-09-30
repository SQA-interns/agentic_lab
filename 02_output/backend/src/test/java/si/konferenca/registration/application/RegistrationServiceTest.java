package si.konferenca.registration.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import si.konferenca.registration.domain.Category;
import si.konferenca.registration.domain.ConferenceOption;
import si.konferenca.registration.domain.ConsentDefinition;
import si.konferenca.registration.domain.FieldError;
import si.konferenca.registration.domain.OptionsCatalog;
import si.konferenca.registration.domain.Registration;
import si.konferenca.registration.domain.RegistrationSubmission;
import si.konferenca.registration.domain.RegistrationType;

class RegistrationServiceTest {

  static final OptionsCatalog CATALOG =
      new OptionsCatalog(
          List.of(
              new ConferenceOption(
                  "ws", "W", Category.WORKSHOP, true, EnumSet.allOf(RegistrationType.class))),
          Map.of(),
          List.of(new ConsentDefinition("dp", "I agree.", true)));

  final NotificationServiceTest.FakeStore store = new NotificationServiceTest.FakeStore();
  final List<String> copyEvents = new ArrayList<>();
  final List<String> captchaCalls = new ArrayList<>();
  boolean captchaAnswer = true;
  boolean copyFails;

  final JsonCopyStore copies =
      new JsonCopyStore() {
        @Override
        public void write(Registration r) {
          if (copyFails) {
            throw new StorageException("disk", null);
          }
          copyEvents.add("write " + r.reference());
        }

        @Override
        public byte[] read(UUID reference) {
          return new byte[0];
        }

        @Override
        public void delete(UUID reference) {
          copyEvents.add("delete " + reference);
        }

        @Override
        public boolean writable() {
          return true;
        }
      };

  final CaptchaVerifier captcha =
      new CaptchaVerifier() {
        @Override
        public boolean verify(String token) {
          captchaCalls.add(token);
          return captchaAnswer;
        }

        @Override
        public boolean testMode() {
          return false;
        }
      };

  final RegistrationService service =
      new RegistrationService(CATALOG, captcha, store, copies, NotificationServiceTest.CLOCK);

  @BeforeEach
  void startSynchronization() {
    TransactionSynchronizationManager.initSynchronization();
  }

  @AfterEach
  void clearSynchronization() {
    TransactionSynchronizationManager.clearSynchronization();
  }

  static RegistrationSubmission submission(String email) {
    return new RegistrationSubmission(
        "EXTERNAL",
        "Ana",
        "Novak",
        email,
        "IJS",
        null,
        null,
        null,
        List.of("ws"),
        List.of("dp"),
        "tok");
  }

  private void complete(int status) {
    for (TransactionSynchronization s : TransactionSynchronizationManager.getSynchronizations()) {
      s.afterCompletion(status);
    }
  }

  @Test
  void storesTheRowAndTheCopyAndKeepsThemOnCommit() {
    Registration r = service.register(submission("a@example.si"));

    assertThat(store.all).containsExactly(r);
    assertThat(r.submittedAt()).isEqualTo(NotificationServiceTest.NOW);
    assertThat(copyEvents).containsExactly("write " + r.reference());
    assertThat(captchaCalls).containsExactly("tok");
    complete(TransactionSynchronization.STATUS_COMMITTED);
    assertThat(copyEvents).hasSize(1);
  }

  @Test
  void deletesTheCopyWhenTheTransactionRollsBack() {
    Registration r = service.register(submission("a@example.si"));

    complete(TransactionSynchronization.STATUS_ROLLED_BACK);

    assertThat(copyEvents).containsExactly("write " + r.reference(), "delete " + r.reference());
  }

  @Test
  void invalidSubmissionIsRejectedWithoutSpendingTheCaptcha() {
    assertThatThrownBy(() -> service.register(submission("bad")))
        .isInstanceOf(RegistrationRejectedException.class)
        .satisfies(
            e ->
                assertThat(((RegistrationRejectedException) e).errors())
                    .extracting(FieldError::field)
                    .containsExactly("email"));
    assertThat(captchaCalls).isEmpty();
    assertThat(store.all).isEmpty();
  }

  @Test
  void failedCaptchaIsRejected() {
    captchaAnswer = false;

    assertThatThrownBy(() -> service.register(submission("a@example.si")))
        .isInstanceOf(RegistrationRejectedException.class)
        .satisfies(
            e ->
                assertThat(((RegistrationRejectedException) e).errors())
                    .extracting(FieldError::field)
                    .containsExactly("captchaToken"));
    assertThat(store.all).isEmpty();
  }

  @Test
  void copyFailurePropagates() {
    copyFails = true;

    assertThatThrownBy(() -> service.register(submission("a@example.si")))
        .isInstanceOf(StorageException.class);
  }
}

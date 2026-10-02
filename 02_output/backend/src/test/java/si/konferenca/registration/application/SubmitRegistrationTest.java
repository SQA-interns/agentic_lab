package si.konferenca.registration.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import si.konferenca.registration.application.SubmitRegistration.ConsentTerms;
import si.konferenca.registration.domain.CaptchaVerifier;
import si.konferenca.registration.domain.CaptchaVerifier.CaptchaUnavailableException;
import si.konferenca.registration.domain.ConferenceOption;
import si.konferenca.registration.domain.JsonCopyStore;
import si.konferenca.registration.domain.MailNotifier;
import si.konferenca.registration.domain.OptionsCatalogue;
import si.konferenca.registration.domain.Registration;
import si.konferenca.registration.domain.RegistrationInput;
import si.konferenca.registration.domain.RegistrationStore;
import si.konferenca.registration.domain.RegistrationType;
import si.konferenca.registration.domain.RegistrationValidator;
import si.konferenca.registration.domain.StorageFailedException;
import si.konferenca.registration.domain.TextField;
import si.konferenca.registration.domain.ValidationFailedException;

/** The order of the acceptance steps and what happens when one of them fails (AR-05, D-10). */
class SubmitRegistrationTest {

  private static final Instant NOW = Instant.parse("2026-10-02T10:15:30.123456789Z");

  /** Every call to a port, in order. */
  private final List<String> calls = new ArrayList<>();

  private boolean captchaPasses = true;
  private RuntimeException captchaFailure;
  private RuntimeException insertFailure;
  private RuntimeException copyFailure;
  private RuntimeException commitFailure;
  private RuntimeException participantMailFailure;
  private RuntimeException organizerMailFailure;

  private final OptionsCatalogue catalogue =
      new OptionsCatalogue() {
        @Override
        public List<ConferenceOption> activeOptions() {
          return List.of();
        }

        @Override
        public Optional<ConferenceOption> findActive(String id) {
          return Optional.empty();
        }
      };

  private final CaptchaVerifier captcha =
      token -> {
        calls.add("captcha");
        if (captchaFailure != null) {
          throw captchaFailure;
        }
        return captchaPasses;
      };

  private final RegistrationStore store =
      new RegistrationStore() {
        @Override
        public void insert(Registration registration) {
          calls.add("insert");
          if (insertFailure != null) {
            throw insertFailure;
          }
        }

        @Override
        public List<Registration> findAll() {
          return List.of();
        }
      };

  private final JsonCopyStore copies =
      new JsonCopyStore() {
        @Override
        public void write(Registration registration) {
          calls.add("copy");
          if (copyFailure != null) {
            throw copyFailure;
          }
        }

        @Override
        public byte[] read(UUID id) {
          calls.add("read-copy");
          return new byte[] {1, 2, 3};
        }

        @Override
        public void delete(UUID id) {
          calls.add("delete-copy");
        }
      };

  private final MailNotifier mails =
      new MailNotifier() {
        @Override
        public void sendParticipantConfirmation(Registration registration) {
          calls.add("participant-mail");
          if (participantMailFailure != null) {
            throw participantMailFailure;
          }
        }

        @Override
        public void sendOrganizerNotification(Registration registration, byte[] jsonCopy) {
          calls.add("organizer-mail:" + jsonCopy.length);
          if (organizerMailFailure != null) {
            throw organizerMailFailure;
          }
        }
      };

  private final SubmitRegistration submit =
      new SubmitRegistration(
          new RegistrationValidator(catalogue),
          captcha,
          work -> {
            calls.add("begin");
            work.run();
            if (commitFailure != null) {
              throw commitFailure;
            }
            calls.add("commit");
          },
          store,
          copies,
          mails,
          new ConsentTerms("personal-data", "Soglašam."),
          Clock.fixed(NOW, ZoneOffset.UTC));

  private static RegistrationInput validInput() {
    Map<TextField, String> texts = new EnumMap<>(TextField.class);
    texts.put(TextField.FIRST_NAME, "Ana");
    texts.put(TextField.LAST_NAME, "Novak");
    texts.put(TextField.EMAIL, "ana.novak@example.org");
    texts.put(TextField.ORGANIZATION, "Podjetje");
    return new RegistrationInput(
        RegistrationType.EXTERNAL, texts, List.of(), true, "token", List.of(), Set.of());
  }

  @Test
  void ar05_rowThenCopyThenCommitThenEmails() {
    Registration registration = submit.submit(validInput());

    assertThat(calls)
        .containsExactly(
            "captcha",
            "begin",
            "insert",
            "copy",
            "commit",
            "participant-mail",
            "read-copy",
            "organizer-mail:3");
    assertThat(registration.acceptedAt()).isEqualTo(Instant.parse("2026-10-02T10:15:30.123Z"));
    assertThat(registration.consent().givenAt()).isEqualTo(registration.acceptedAt());
    assertThat(registration.consent().text()).isEqualTo("Soglašam.");
  }

  @Test
  void invalidInputCostsNoVerificationCallAndTouchesNothing() {
    RegistrationInput input =
        new RegistrationInput(
            RegistrationType.EXTERNAL, Map.of(), List.of(), true, "token", List.of(), Set.of());

    assertThatThrownBy(() -> submit.submit(input)).isInstanceOf(ValidationFailedException.class);
    assertThat(calls).isEmpty();
  }

  @Test
  void sr01_refusedTokenRejectsBeforeAnythingIsStored() {
    captchaPasses = false;

    assertThatThrownBy(() -> submit.submit(validInput()))
        .isInstanceOfSatisfying(
            ValidationFailedException.class,
            failure ->
                assertThat(failure.errors())
                    .extracting(error -> error.field() + ":" + error.code())
                    .containsExactly("captchaToken:captcha_failed"));
    assertThat(calls).containsExactly("captcha");
  }

  @Test
  void sr01_unreachableVerificationStoresNothing() {
    captchaFailure = new CaptchaUnavailableException("down", null);

    assertThatThrownBy(() -> submit.submit(validInput()))
        .isInstanceOf(CaptchaUnavailableException.class);
    assertThat(calls).containsExactly("captcha");
  }

  @Test
  void ar05_databaseFailureMeansNoCopyAndNoEmail() {
    insertFailure = new IllegalStateException("database down");

    assertThatThrownBy(() -> submit.submit(validInput()))
        .isInstanceOf(StorageFailedException.class)
        .hasMessageNotContaining("ana.novak");
    assertThat(calls).containsExactly("captcha", "begin", "insert", "delete-copy");
  }

  @Test
  void ar05_copyFailureMeansNoCommitAndNoEmail() {
    copyFailure = new IllegalStateException("disk full");

    assertThatThrownBy(() -> submit.submit(validInput()))
        .isInstanceOf(StorageFailedException.class);
    assertThat(calls).containsExactly("captcha", "begin", "insert", "copy", "delete-copy");
  }

  @Test
  void ar05_commitFailureRemovesTheCopyAndSendsNoEmail() {
    commitFailure = new IllegalStateException("commit failed");

    assertThatThrownBy(() -> submit.submit(validInput()))
        .isInstanceOf(StorageFailedException.class);
    assertThat(calls).containsExactly("captcha", "begin", "insert", "copy", "delete-copy");
  }

  @Test
  void d10_participantMailFailureStillAcceptsAndStillNotifiesTheOrganizer() {
    participantMailFailure = new IllegalStateException("smtp down for ana.novak@example.org");

    Registration registration = submit.submit(validInput());

    assertThat(registration.id()).isNotNull();
    assertThat(calls).endsWith("participant-mail", "read-copy", "organizer-mail:3");
    assertThat(calls).doesNotContain("delete-copy");
  }

  @Test
  void d10_organizerMailFailureStillAccepts() {
    organizerMailFailure = new IllegalStateException("smtp down");

    assertThat(submit.submit(validInput()).id()).isNotNull();
    assertThat(calls).doesNotContain("delete-copy");
  }
}

package si.konferenca.registration.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RegistrationTest {

  private static final Instant NOW = Instant.parse("2026-09-30T10:00:00Z");

  private static Registration registration() {
    ParticipantDetails p =
        new ParticipantDetails(
            RegistrationType.EXTERNAL, "Ana", "Novak", "a@example.si", "IJS", null, null, null);
    return Registration.accept(
        UUID.randomUUID(),
        p,
        List.of(
            new ConferenceOption(
                "ws", "Workshop", Category.WORKSHOP, true, EnumSet.allOf(RegistrationType.class))),
        List.of(new ConsentDefinition("dp", "I agree.", true)),
        NOW);
  }

  @Test
  void acceptCopiesParticipantOptionsAndConsents() {
    Registration r = registration();

    assertThat(r.participantMailStatus()).isEqualTo(MailStatus.PENDING);
    assertThat(r.organizerMailStatus()).isEqualTo(MailStatus.PENDING);
    assertThat(r.mailPending()).isTrue();
    assertThat(r.submittedAt()).isEqualTo(NOW);
    assertThat(r.options())
        .singleElement()
        .extracting(RegistrationOption::optionName)
        .isEqualTo("Workshop");
    assertThat(r.consents())
        .singleElement()
        .satisfies(
            c -> {
              assertThat(c.consentId()).isEqualTo("dp");
              assertThat(c.consentText()).isEqualTo("I agree.");
              assertThat(c.givenAt()).isEqualTo(NOW);
            });
  }

  @Test
  void recordsAttemptsAndGivesUpAfterTheMaximum() {
    Registration r = registration();

    r.recordMailAttempt(MailStatus.SENT, MailStatus.FAILED, NOW, 2);
    assertThat(r.mailAttempts()).isEqualTo(1);
    assertThat(r.organizerMailStatus()).isEqualTo(MailStatus.FAILED);
    assertThat(r.lastMailAttemptAt()).isEqualTo(NOW);
    assertThat(r.mailPending()).isTrue();

    r.recordMailAttempt(MailStatus.SENT, MailStatus.FAILED, NOW.plusSeconds(1), 2);
    assertThat(r.mailAttempts()).isEqualTo(2);
    assertThat(r.organizerMailStatus()).isEqualTo(MailStatus.ABANDONED);
    assertThat(r.participantMailStatus()).isEqualTo(MailStatus.SENT);
    assertThat(r.mailPending()).isFalse();
  }

  @Test
  void mailStatusNeedsSending() {
    assertThat(EnumSet.allOf(MailStatus.class).stream().filter(MailStatus::needsSending))
        .containsExactlyInAnyOrder(MailStatus.PENDING, MailStatus.FAILED);
  }

  @Test
  void optionOfferedOnlyWhenActiveAndForType() {
    ConferenceOption studentOnly =
        new ConferenceOption("x", "X", Category.EVENT, true, Set.of(RegistrationType.STUDENT));
    ConferenceOption inactive =
        new ConferenceOption(
            "y", "Y", Category.EVENT, false, EnumSet.allOf(RegistrationType.class));

    assertThat(studentOnly.offeredTo(RegistrationType.STUDENT)).isTrue();
    assertThat(studentOnly.offeredTo(RegistrationType.EXTERNAL)).isFalse();
    assertThat(inactive.offeredTo(RegistrationType.STUDENT)).isFalse();
  }
}

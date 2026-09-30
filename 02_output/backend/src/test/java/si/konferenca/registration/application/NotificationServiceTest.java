package si.konferenca.registration.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import si.konferenca.registration.domain.Category;
import si.konferenca.registration.domain.ConferenceOption;
import si.konferenca.registration.domain.ConsentDefinition;
import si.konferenca.registration.domain.MailStatus;
import si.konferenca.registration.domain.ParticipantDetails;
import si.konferenca.registration.domain.Registration;
import si.konferenca.registration.domain.RegistrationType;

class NotificationServiceTest {

  static final Instant NOW = Instant.parse("2026-09-30T10:00:00Z");
  static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

  static Registration registration(RegistrationType type) {
    ParticipantDetails p =
        type == RegistrationType.EXTERNAL
            ? new ParticipantDetails(type, "Ana", "Novak", "a@example.si", "IJS", null, null, null)
            : new ParticipantDetails(type, "Luka", "Kovač", "l@example.si", null, "UL", "RI", "63");
    return Registration.accept(
        UUID.randomUUID(),
        p,
        List.of(
            new ConferenceOption(
                "ws", "Workshop A", Category.WORKSHOP, true, EnumSet.allOf(RegistrationType.class)),
            new ConferenceOption(
                "m", "Lunch", Category.MEAL, true, EnumSet.allOf(RegistrationType.class))),
        List.of(new ConsentDefinition("dp", "I agree.", true)),
        NOW.minusSeconds(120));
  }

  /** In-memory store recording saved statuses. */
  static class FakeStore implements RegistrationStore {
    final List<Registration> all = new ArrayList<>();
    int statusSaves;
    Instant lastPendingBefore;

    @Override
    public Registration insert(Registration r) {
      all.add(r);
      return r;
    }

    @Override
    public Optional<Registration> findByReference(UUID reference) {
      return all.stream().filter(r -> r.reference().equals(reference)).findFirst();
    }

    @Override
    public List<Registration> findAllOldestFirst() {
      return all;
    }

    @Override
    public List<Registration> findNeedingMail(Instant pendingBefore) {
      lastPendingBefore = pendingBefore;
      return all.stream().filter(Registration::mailPending).toList();
    }

    @Override
    public void saveMailStatus(Registration r) {
      statusSaves++;
    }
  }

  /** Copy store holding bytes per reference. */
  static final class FakeCopies implements JsonCopyStore {
    boolean missing;

    @Override
    public void write(Registration r) {}

    @Override
    public byte[] read(UUID reference) {
      if (missing) {
        throw new StorageException("missing", null);
      }
      return ("{\"reference\":\"" + reference + "\"}").getBytes();
    }

    @Override
    public void delete(UUID reference) {}

    @Override
    public boolean writable() {
      return true;
    }
  }

  /** Mailer that fails while {@code failing} is set. */
  static final class FakeMailer implements Mailer {
    final List<OutgoingMail> sent = new ArrayList<>();
    boolean failing;

    @Override
    public void send(OutgoingMail mail) {
      if (failing) {
        throw new MailDeliveryException("down", null);
      }
      sent.add(mail);
    }
  }

  final FakeStore store = new FakeStore();
  final FakeCopies copies = new FakeCopies();
  final FakeMailer mailer = new FakeMailer();
  final NotificationService service =
      new NotificationService(
          store,
          copies,
          mailer,
          new ConferenceSettings("Konferenca", List.of("o1@x.si", "o2@x.si"), "", 3),
          CLOCK);

  @Test
  void sendsBothEmailsOnce() {
    Registration r = store.insert(registration(RegistrationType.EXTERNAL));

    service.notifyAccepted(r.reference());
    service.notifyAccepted(r.reference());

    assertThat(mailer.sent).hasSize(2);
    assertThat(mailer.sent.get(0).to()).containsExactly("a@example.si");
    assertThat(mailer.sent.get(1).to()).containsExactly("o1@x.si", "o2@x.si");
    assertThat(mailer.sent.get(1).attachmentName())
        .isEqualTo("registration-" + r.reference() + ".json");
    assertThat(new String(mailer.sent.get(1).attachment())).contains(r.reference().toString());
    assertThat(r.participantMailStatus()).isEqualTo(MailStatus.SENT);
    assertThat(r.organizerMailStatus()).isEqualTo(MailStatus.SENT);
    assertThat(store.statusSaves).isEqualTo(1);
  }

  @Test
  void failureIsRecordedAndRetriedUntilAbandoned() {
    Registration r = store.insert(registration(RegistrationType.STUDENT));
    mailer.failing = true;

    service.notifyAccepted(r.reference());
    assertThat(r.participantMailStatus()).isEqualTo(MailStatus.FAILED);
    assertThat(r.organizerMailStatus()).isEqualTo(MailStatus.FAILED);

    service.retryDue();
    service.retryDue();
    assertThat(r.participantMailStatus()).isEqualTo(MailStatus.ABANDONED);
    assertThat(r.mailAttempts()).isEqualTo(3);
    assertThat(store.lastPendingBefore).isEqualTo(NOW.minus(NotificationService.PENDING_GRACE));

    mailer.failing = false;
    service.retryDue();
    assertThat(mailer.sent).isEmpty();
  }

  @Test
  void retryDeliversAfterRecovery() {
    Registration r = store.insert(registration(RegistrationType.EXTERNAL));
    mailer.failing = true;
    service.notifyAccepted(r.reference());
    mailer.failing = false;

    service.retryDue();

    assertThat(mailer.sent).hasSize(2);
    assertThat(r.mailPending()).isFalse();
  }

  @Test
  void organizerMailFailsWhenTheCopyCannotBeRead() {
    Registration r = store.insert(registration(RegistrationType.EXTERNAL));
    copies.missing = true;

    service.notifyAccepted(r.reference());

    assertThat(r.participantMailStatus()).isEqualTo(MailStatus.SENT);
    assertThat(r.organizerMailStatus()).isEqualTo(MailStatus.FAILED);
    assertThat(mailer.sent).hasSize(1);
  }

  @Test
  void unknownReferenceAndStoreErrorsNeverThrow() {
    service.notifyAccepted(UUID.randomUUID());

    NotificationService broken =
        new NotificationService(
            new FakeStore() {
              @Override
              public Optional<Registration> findByReference(UUID reference) {
                throw new IllegalStateException("db down");
              }
            },
            copies,
            mailer,
            new ConferenceSettings("K", List.of("o@x.si"), "", 3),
            CLOCK);
    broken.notifyAccepted(UUID.randomUUID());

    assertThat(mailer.sent).isEmpty();
  }
}

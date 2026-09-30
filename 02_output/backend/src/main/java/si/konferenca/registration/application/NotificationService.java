package si.konferenca.registration.application;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import si.konferenca.registration.domain.MailStatus;
import si.konferenca.registration.domain.Registration;

/**
 * Sends the participant and organizer emails after a registration is committed and retries failed
 * ones (US-006, US-007, D-11). A mail failure never affects the registration.
 */
@Service
public class NotificationService {

  /** A PENDING email older than this is treated as interrupted and sent by the retry job. */
  static final Duration PENDING_GRACE = Duration.ofMinutes(1);

  private static final Logger LOG = LoggerFactory.getLogger(NotificationService.class);

  private final RegistrationStore store;
  private final JsonCopyStore copies;
  private final Mailer mailer;
  private final ConferenceSettings settings;
  private final Clock clock;

  public NotificationService(
      RegistrationStore store,
      JsonCopyStore copies,
      Mailer mailer,
      ConferenceSettings settings,
      Clock clock) {
    this.store = store;
    this.copies = copies;
    this.mailer = mailer;
    this.settings = settings;
    this.clock = clock;
  }

  /** Sends the emails of a newly committed registration; never throws. */
  public void notifyAccepted(UUID reference) {
    try {
      store.findByReference(reference).ifPresent(this::deliver);
    } catch (RuntimeException e) {
      LOG.warn("Notification for registration {} could not be processed", reference, e);
    }
  }

  /** Retries every email that failed or was interrupted (scheduled). */
  public void retryDue() {
    for (Registration r : store.findNeedingMail(clock.instant().minus(PENDING_GRACE))) {
      try {
        deliver(r);
      } catch (RuntimeException e) {
        LOG.warn("Mail retry for registration {} could not be processed", r.reference(), e);
      }
    }
  }

  private void deliver(Registration r) {
    if (!r.mailPending()) {
      return;
    }
    MailStatus participant = r.participantMailStatus();
    if (participant.needsSending()) {
      participant = send(r, MailComposer.participantMail(r, settings.conferenceName()));
    }
    MailStatus organizer = r.organizerMailStatus();
    if (organizer.needsSending()) {
      organizer = sendOrganizerMail(r);
    }
    r.recordMailAttempt(participant, organizer, clock.instant(), settings.mailMaxAttempts());
    store.saveMailStatus(r);
    if (r.participantMailStatus() == MailStatus.ABANDONED
        || r.organizerMailStatus() == MailStatus.ABANDONED) {
      LOG.error(
          "Giving up on email for registration {} after {} attempts",
          r.reference(),
          r.mailAttempts());
    }
  }

  private MailStatus sendOrganizerMail(Registration r) {
    byte[] copy;
    try {
      copy = copies.read(r.reference());
    } catch (StorageException e) {
      LOG.warn(
          "JSON copy of registration {} could not be read for the organizer email", r.reference());
      return MailStatus.FAILED;
    }
    return send(
        r,
        MailComposer.organizerMail(r, settings.conferenceName(), settings.organizerEmails(), copy));
  }

  private MailStatus send(Registration r, OutgoingMail mail) {
    try {
      mailer.send(mail);
      return MailStatus.SENT;
    } catch (MailDeliveryException e) {
      LOG.warn("Email for registration {} was not accepted by the mail server", r.reference());
      return MailStatus.FAILED;
    }
  }
}

package si.konferenca.registration.infrastructure;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import si.konferenca.registration.domain.MailStatus;
import si.konferenca.registration.domain.Registration;

/** Spring Data repository for registrations (parameterised queries only, SB-05). */
public interface JpaRegistrationRepository extends JpaRepository<Registration, Long> {

  Optional<Registration> findByReference(UUID reference);

  List<Registration> findAllByOrderBySubmittedAtAscIdAsc();

  @Query(
      "select r from Registration r where r.participantMailStatus = :failed"
          + " or r.organizerMailStatus = :failed"
          + " or ((r.participantMailStatus = :pending or r.organizerMailStatus = :pending)"
          + " and r.submittedAt < :pendingBefore)"
          + " order by r.submittedAt")
  List<Registration> findNeedingMail(
      @Param("failed") MailStatus failed,
      @Param("pending") MailStatus pending,
      @Param("pendingBefore") Instant pendingBefore);

  @Modifying
  @Query(
      "update Registration r set r.participantMailStatus = :participant,"
          + " r.organizerMailStatus = :organizer, r.mailAttempts = :attempts,"
          + " r.lastMailAttemptAt = :at where r.id = :id")
  int updateMailStatus(
      @Param("id") Long id,
      @Param("participant") MailStatus participant,
      @Param("organizer") MailStatus organizer,
      @Param("attempts") int attempts,
      @Param("at") Instant at);
}

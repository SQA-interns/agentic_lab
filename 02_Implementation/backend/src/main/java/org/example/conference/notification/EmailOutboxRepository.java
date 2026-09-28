package org.example.conference.notification;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Outbox persistence; due rows are claimed with row locks that skip concurrent claimers. */
public interface EmailOutboxRepository extends JpaRepository<EmailOutbox, UUID> {

  @Query(
      value =
          "SELECT * FROM email_outbox WHERE status = 'PENDING' AND next_attempt_at <= :now"
              + " ORDER BY next_attempt_at LIMIT :limit FOR UPDATE SKIP LOCKED",
      nativeQuery = true)
  List<EmailOutbox> lockDue(@Param("now") Instant now, @Param("limit") int limit);

  List<EmailOutbox> findByRegistrationId(UUID registrationId);
}

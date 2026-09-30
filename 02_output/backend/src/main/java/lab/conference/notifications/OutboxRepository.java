package lab.conference.notifications;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Outbox access; due rows are claimed with row locks so concurrent workers never share one. */
public interface OutboxRepository extends JpaRepository<OutboxEntry, Long> {

  @Query(
      value =
          "SELECT * FROM notification_outbox WHERE status = 'PENDING' AND next_attempt_at <= :now"
              + " ORDER BY next_attempt_at, id LIMIT :limit FOR UPDATE SKIP LOCKED",
      nativeQuery = true)
  List<OutboxEntry> claimDue(@Param("now") Instant now, @Param("limit") int limit);
}

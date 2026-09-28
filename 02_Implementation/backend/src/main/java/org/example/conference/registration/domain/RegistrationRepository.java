package org.example.conference.registration.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Registration persistence. */
public interface RegistrationRepository extends JpaRepository<Registration, UUID> {

  Optional<Registration> findByClientRequestId(UUID clientRequestId);

  @Query("select r.id as id, r.rawJsonSha256 as rawJsonSha256 from Registration r")
  List<RegistrationDigest> findAllDigests();

  @Query(
      "select distinct r from Registration r left join fetch r.selections"
          + " order by r.createdAt, r.id")
  List<Registration> findAllWithSelections();

  /** Projection used by reconciliation. */
  interface RegistrationDigest {
    UUID getId();

    String getRawJsonSha256();
  }
}

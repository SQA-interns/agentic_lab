package org.conference.registration.repository;

import java.util.List;
import java.util.UUID;
import org.conference.registration.domain.Registration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RegistrationRepository extends JpaRepository<Registration, UUID> {

  /** All registrations with their options, oldest first (Excel export, US-008). */
  @Query("select distinct r from Registration r left join fetch r.options order by r.createdAt")
  List<Registration> findAllWithOptions();
}

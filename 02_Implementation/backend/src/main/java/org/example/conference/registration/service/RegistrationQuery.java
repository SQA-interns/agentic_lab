package org.example.conference.registration.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.example.conference.registration.domain.Registration;
import org.example.conference.registration.domain.RegistrationRepository;
import org.example.conference.registration.domain.RegistrationSelection;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only API of accepted registrations for other modules (export). */
@Service
public class RegistrationQuery {

  private final RegistrationRepository repository;

  public RegistrationQuery(RegistrationRepository repository) {
    this.repository = repository;
  }

  /** Flat read model of an accepted registration; selections are option names per group key. */
  public record RegistrationView(
      UUID id,
      Instant submittedAt,
      String participantType,
      String firstName,
      String lastName,
      String email,
      String organization,
      String studyInstitution,
      String studyProgramme,
      String studentId,
      Map<String, List<String>> selectionsByGroup,
      List<String> consentIds) {

    public RegistrationView {
      selectionsByGroup = Map.copyOf(selectionsByGroup);
      consentIds = List.copyOf(consentIds);
    }
  }

  @Transactional(readOnly = true)
  public List<RegistrationView> findAll() {
    return repository.findAllWithSelections().stream().map(RegistrationQuery::toView).toList();
  }

  private static RegistrationView toView(Registration r) {
    Map<String, List<String>> selections =
        r.getSelections().stream()
            .collect(
                Collectors.groupingBy(
                    RegistrationSelection::getOptionGroup,
                    Collectors.mapping(RegistrationSelection::getOptionName, Collectors.toList())));
    return new RegistrationView(
        r.getId(),
        r.getCreatedAt(),
        r.getParticipantType().name(),
        r.getFirstName(),
        r.getLastName(),
        r.getEmail(),
        r.getOrganization(),
        r.getStudyInstitution(),
        r.getStudyProgramme(),
        r.getStudentId(),
        selections,
        r.getConsents().stream().map(c -> c.getConsentId()).toList());
  }
}

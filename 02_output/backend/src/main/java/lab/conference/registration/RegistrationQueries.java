package lab.conference.registration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lab.conference.options.GroupId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read access to current accepted registrations (DB rows only, so orphans never appear). */
@Service
public class RegistrationQueries {

  private final RegistrationRepository repository;

  public RegistrationQueries(RegistrationRepository repository) {
    this.repository = repository;
  }

  @Transactional(readOnly = true)
  public List<AcceptedRegistration> allAccepted() {
    List<AcceptedRegistration> out = new ArrayList<>();
    for (RegistrationEntity r : repository.findAllInAcceptanceOrder()) {
      Map<String, String> p = new HashMap<>();
      p.put("firstName", r.firstName());
      p.put("lastName", r.lastName());
      p.put("email", r.email());
      p.put("organization", r.organization());
      p.put("studyInstitution", r.studyInstitution());
      p.put("studyProgramme", r.studyProgramme());
      p.put("studentId", r.studentId());
      Map<String, List<String>> names = new LinkedHashMap<>();
      for (GroupId g : GroupId.values()) {
        names.put(g.key(), new ArrayList<>());
      }
      for (RegistrationEntity.Selection s : r.selections()) {
        names.computeIfAbsent(s.groupId(), k -> new ArrayList<>()).add(s.optionName());
      }
      out.add(
          new AcceptedRegistration(
              r.id(),
              r.clientRequestId(),
              r.formType(),
              r.acceptedAt(),
              p,
              names,
              r.consentGiven()));
    }
    return out;
  }
}

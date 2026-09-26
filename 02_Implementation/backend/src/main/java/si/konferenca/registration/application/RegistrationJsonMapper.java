package si.konferenca.registration.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import si.konferenca.registration.domain.OptionCategory;
import si.konferenca.registration.domain.Registration;
import si.konferenca.registration.domain.RegistrationType;

/** Produces the raw JSON representation used for the file backup and the organizer email. */
@Component
public class RegistrationJsonMapper {

  private final ObjectMapper objectMapper;

  public RegistrationJsonMapper(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public String toJson(Registration registration) {
    try {
      return objectMapper
          .writerWithDefaultPrettyPrinter()
          .writeValueAsString(Document.of(registration));
    } catch (JsonProcessingException e) {
      throw new RegistrationStorageException("Could not serialise registration", e);
    }
  }

  record Document(
      UUID id,
      Instant createdAt,
      RegistrationType type,
      String firstName,
      String lastName,
      String email,
      String organization,
      String studyInstitution,
      String studyProgramme,
      String studentId,
      List<Option> selectedOptions,
      List<String> consents) {

    static Document of(Registration r) {
      return new Document(
          r.getId(),
          r.getCreatedAt(),
          r.getType(),
          r.getFirstName(),
          r.getLastName(),
          r.getEmail(),
          r.getOrganization(),
          r.getStudyInstitution(),
          r.getStudyProgramme(),
          r.getStudentId(),
          r.getSelectedOptions().stream()
              .map(o -> new Option(o.getOptionId(), o.getOptionName(), o.getCategory()))
              .toList(),
          r.getConsentIds());
    }
  }

  record Option(String id, String name, OptionCategory category) {}
}

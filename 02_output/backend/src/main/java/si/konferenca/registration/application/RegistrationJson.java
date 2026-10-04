package si.konferenca.registration.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import si.konferenca.registration.domain.Participant;
import si.konferenca.registration.domain.Registration;
import tools.jackson.databind.json.JsonMapper;

/** Serializes the raw JSON copy of an accepted registration (registration-copy.schema.json). */
public final class RegistrationJson {

  private final JsonMapper mapper;

  public RegistrationJson(JsonMapper mapper) {
    this.mapper = mapper;
  }

  public byte[] toJson(Registration registration) {
    Map<String, Object> copy = new LinkedHashMap<>();
    copy.put("schemaVersion", 1);
    copy.put("id", registration.id().toString());
    copy.put("type", registration.type().name());
    copy.put("acceptedAt", registration.acceptedAt().toString());
    copy.put("participant", participant(registration.participant()));
    List<Map<String, Object>> options =
        registration.options().stream()
            .map(
                o -> {
                  Map<String, Object> option = new LinkedHashMap<>();
                  option.put("id", o.optionId());
                  option.put("name", o.optionName());
                  option.put("category", o.category().value());
                  return option;
                })
            .toList();
    copy.put("options", options);
    Map<String, Object> consent = new LinkedHashMap<>();
    consent.put("id", registration.consentId());
    consent.put("text", registration.consentText());
    consent.put("givenAt", registration.consentGivenAt().toString());
    copy.put("consent", consent);
    return mapper.writeValueAsBytes(copy);
  }

  private static Map<String, Object> participant(Participant p) {
    Map<String, Object> participant = new LinkedHashMap<>();
    participant.put("firstName", p.firstName());
    participant.put("lastName", p.lastName());
    participant.put("email", p.email());
    putIfPresent(participant, "organization", p.organization());
    putIfPresent(participant, "studyInstitution", p.studyInstitution());
    putIfPresent(participant, "studyProgramme", p.studyProgramme());
    putIfPresent(participant, "studentId", p.studentId());
    return participant;
  }

  private static void putIfPresent(Map<String, Object> map, String key, String value) {
    if (value != null) {
      map.put(key, value);
    }
  }
}

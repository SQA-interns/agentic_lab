package si.konferenca.registration.infrastructure;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import si.konferenca.registration.domain.ParticipantDetails;
import si.konferenca.registration.domain.Registration;
import tools.jackson.databind.json.JsonMapper;

/** Serialises a registration as docs/02_contracts/registration-copy.schema.json, in UTF-8. */
final class RegistrationCopyJson {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  private RegistrationCopyJson() {}

  static byte[] toBytes(Registration registration) {
    Map<String, Object> copy = new LinkedHashMap<>();
    copy.put("registrationId", registration.id().toString());
    copy.put("registeredAt", registration.registeredAt().toString());
    copy.put("type", registration.type().name());
    copy.put("participant", participant(registration.participant()));
    copy.put(
        "options",
        registration.options().stream()
            .map(
                option ->
                    ordered(
                        "id", option.optionId(),
                        "name", option.optionName(),
                        "category", option.category().value()))
            .toList());
    copy.put(
        "consents",
        registration.consents().stream()
            .map(
                consent ->
                    ordered(
                        "id", consent.consentId(),
                        "text", consent.consentText(),
                        "givenAt", consent.givenAt().toString()))
            .toList());
    return JSON.writeValueAsBytes(copy);
  }

  private static Map<String, String> participant(ParticipantDetails participant) {
    Map<String, String> fields = new LinkedHashMap<>();
    fields.put("firstName", participant.firstName());
    fields.put("lastName", participant.lastName());
    fields.put("email", participant.email());
    putIfPresent(fields, "organization", participant.organization());
    putIfPresent(fields, "studyInstitution", participant.studyInstitution());
    putIfPresent(fields, "studyProgramme", participant.studyProgramme());
    putIfPresent(fields, "studentId", participant.studentId());
    return fields;
  }

  private static void putIfPresent(Map<String, String> fields, String name, String value) {
    if (value != null) {
      fields.put(name, value);
    }
  }

  private static Map<String, String> ordered(String... keysAndValues) {
    Map<String, String> map = new LinkedHashMap<>();
    List<String> entries = List.of(keysAndValues);
    for (int i = 0; i < entries.size(); i += 2) {
      map.put(entries.get(i), entries.get(i + 1));
    }
    return map;
  }
}

package si.konferenca.registration.service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import si.konferenca.registration.domain.GivenConsent;
import si.konferenca.registration.domain.Participant;
import si.konferenca.registration.domain.Registration;
import si.konferenca.registration.domain.RegistrationType;
import si.konferenca.registration.domain.SelectedOption;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Serializes the raw JSON copy (`registration-copy.schema.json`): UTF-8, pretty-printed, non-ASCII
 * characters unescaped (NFR-01). The same bytes are stored and attached to the organizer email.
 */
public final class RegistrationCopy {

  private static final DateTimeFormatter FILE_TIME =
      DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
  private static final JsonMapper MAPPER =
      JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();

  private RegistrationCopy() {}

  /** `<registeredAt as yyyyMMdd'T'HHmmss'Z'>_<id>.json`. */
  public static String fileName(Registration registration) {
    return FILE_TIME.format(registration.registeredAt()) + "_" + registration.id() + ".json";
  }

  public static byte[] serialize(Registration registration) {
    Map<String, Object> copy = new LinkedHashMap<>();
    copy.put("schemaVersion", 1);
    copy.put("id", registration.id().toString());
    copy.put("type", registration.type().name());
    copy.put("registeredAt", timestamp(registration.registeredAt()));
    copy.put("participant", participant(registration.type(), registration.participant()));
    List<Map<String, Object>> options = new ArrayList<>();
    for (SelectedOption option : registration.options()) {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("id", option.optionId());
      item.put("displayName", option.displayName());
      item.put("category", option.category().name());
      options.add(item);
    }
    copy.put("options", options);
    List<Map<String, Object>> consents = new ArrayList<>();
    for (GivenConsent consent : registration.consents()) {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("id", consent.consentId());
      item.put("text", consent.consentText());
      item.put("givenAt", timestamp(consent.givenAt()));
      consents.add(item);
    }
    copy.put("consents", consents);
    return MAPPER.writeValueAsBytes(copy);
  }

  /** ISO-8601 UTC timestamp used in every output. */
  public static String timestamp(Instant instant) {
    return DateTimeFormatter.ISO_INSTANT.format(instant);
  }

  private static Map<String, Object> participant(RegistrationType type, Participant p) {
    Map<String, Object> fields = new LinkedHashMap<>();
    fields.put("firstName", p.firstName());
    fields.put("lastName", p.lastName());
    fields.put("email", p.email());
    if (type == RegistrationType.EXTERNAL) {
      fields.put("organization", p.organization());
    } else {
      fields.put("studyInstitution", p.studyInstitution());
      fields.put("studyProgramme", p.studyProgramme());
      fields.put("studentId", p.studentId());
    }
    return fields;
  }
}

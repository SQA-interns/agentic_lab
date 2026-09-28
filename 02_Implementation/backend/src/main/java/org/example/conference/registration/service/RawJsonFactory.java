package org.example.conference.registration.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.example.conference.catalog.CatalogOption;
import org.example.conference.catalog.OptionGroup;
import org.springframework.stereotype.Component;

/**
 * Builds the canonical raw JSON representation (schemaVersion 1) and the request fingerprint. The
 * same bytes are stored in the database, the backup file and the organizer attachment.
 */
@Component
public class RawJsonFactory {

  private final ObjectMapper mapper =
      new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

  public String rawJson(UUID registrationId, Instant submittedAt, ValidatedRegistration v) {
    Map<String, Object> root = new LinkedHashMap<>();
    root.put("schemaVersion", 1);
    root.put("registrationId", registrationId.toString());
    root.put("participantType", v.command().participantType().name());
    root.put("submittedAt", submittedAt.toString());
    root.put("participant", v.command().fields());
    Map<String, Object> selections = new LinkedHashMap<>();
    for (OptionGroup group : OptionGroup.values()) {
      selections.put(group.key(), options(v.selections().get(group)));
    }
    root.put("selections", selections);
    root.put(
        "consents",
        v.grantedConsents().stream()
            .map(c -> orderedMap("id", c.id(), "text", c.text()))
            .toList());
    return write(root);
  }

  /** Fingerprint of the normalized payload (excludes request ID and captcha token). */
  public String fingerprint(ValidatedRegistration v) {
    Map<String, Object> root = new LinkedHashMap<>();
    root.put("participantType", v.command().participantType().name());
    root.put("participant", v.command().fields());
    Map<String, Object> selections = new LinkedHashMap<>();
    for (OptionGroup group : OptionGroup.values()) {
      selections.put(
          group.key(), v.selections().get(group).stream().map(CatalogOption::id).toList());
    }
    root.put("selections", selections);
    root.put("consents", v.grantedConsents().stream().map(c -> c.id()).sorted().toList());
    return Hashing.sha256(write(root));
  }

  private static List<Map<String, Object>> options(List<CatalogOption> options) {
    return options.stream().map(o -> orderedMap("id", o.id(), "name", o.name())).toList();
  }

  private static Map<String, Object> orderedMap(String k1, Object v1, String k2, Object v2) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put(k1, v1);
    map.put(k2, v2);
    return map;
  }

  private String write(Object value) {
    try {
      return mapper.writeValueAsString(value) + "\n";
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Cannot serialize registration JSON", e);
    }
  }
}

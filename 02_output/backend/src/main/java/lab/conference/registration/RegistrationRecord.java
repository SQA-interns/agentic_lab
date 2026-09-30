package lab.conference.registration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lab.conference.options.CatalogOption;
import lab.conference.options.GroupId;

/** Canonical durable JSON (registration-record.schema.json, D-18); UTF-8 bytes. */
public final class RegistrationRecord {

  private static final ObjectMapper JSON = new ObjectMapper();

  private RegistrationRecord() {}

  public static byte[] toJson(UUID registrationId, Instant acceptedAt, ValidatedRegistration r) {
    Map<String, Object> root = new LinkedHashMap<>();
    root.put("schemaVersion", 1);
    root.put("registrationId", registrationId.toString());
    root.put("clientRequestId", r.clientRequestId().toString());
    root.put("formType", r.formType().key());
    root.put("acceptedAt", acceptedAt.toString());
    root.put("participant", new LinkedHashMap<>(r.fields()));
    Map<String, Object> selections = new LinkedHashMap<>();
    for (GroupId g : GroupId.values()) {
      List<Map<String, String>> options = new ArrayList<>();
      for (CatalogOption o : r.selections().getOrDefault(g, List.of())) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("id", o.id());
        m.put("name", o.name());
        options.add(m);
      }
      selections.put(g.key(), options);
    }
    root.put("selections", selections);
    if (r.consent() == null) {
      root.put("consent", null);
    } else {
      Map<String, Object> consent = new LinkedHashMap<>();
      consent.put("id", r.consent().id());
      consent.put("given", r.consent().given());
      root.put("consent", consent);
    }
    try {
      return JSON.writeValueAsBytes(root);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }
}

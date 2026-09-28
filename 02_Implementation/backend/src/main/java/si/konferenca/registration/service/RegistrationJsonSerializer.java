package si.konferenca.registration.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.stereotype.Component;

/** Serializes registration snapshots to the raw JSON backup format (UTF-8, pretty printed). */
@Component
public class RegistrationJsonSerializer {

  private final ObjectMapper objectMapper;

  public RegistrationJsonSerializer(ObjectMapper objectMapper) {
    this.objectMapper =
        objectMapper
            .copy()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
  }

  public byte[] toJson(RegistrationSnapshot snapshot) {
    try {
      return objectMapper.writeValueAsBytes(snapshot);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Registration could not be serialized", e);
    }
  }
}

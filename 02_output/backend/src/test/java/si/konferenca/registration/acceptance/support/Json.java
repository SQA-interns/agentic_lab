package si.konferenca.registration.acceptance.support;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** JSON reading and writing for tests. */
public final class Json {

  private static final JsonMapper MAPPER = JsonMapper.builder().build();

  private Json() {}

  public static String write(Object value) {
    return MAPPER.writeValueAsString(value);
  }

  public static JsonNode read(String json) {
    return MAPPER.readTree(json);
  }
}

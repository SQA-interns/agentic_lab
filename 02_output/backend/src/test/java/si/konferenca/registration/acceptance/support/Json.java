package si.konferenca.registration.acceptance.support;

import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** JSON reading and writing for the tests. */
public final class Json {

  private static final JsonMapper MAPPER = JsonMapper.builder().build();

  private Json() {}

  public static String write(Object value) {
    return MAPPER.writeValueAsString(value);
  }

  public static JsonNode read(byte[] json) {
    return MAPPER.readTree(json);
  }

  public static JsonNode read(String json) {
    return MAPPER.readTree(json);
  }

  /** The string values of one property of every element of an array node. */
  public static List<String> strings(JsonNode array, String property) {
    List<String> values = new ArrayList<>();
    for (JsonNode element : array) {
      values.add(element.path(property).asString());
    }
    return values;
  }

  /** The string values of an array of strings. */
  public static List<String> strings(JsonNode array) {
    List<String> values = new ArrayList<>();
    for (JsonNode element : array) {
      values.add(element.asString());
    }
    return values;
  }
}

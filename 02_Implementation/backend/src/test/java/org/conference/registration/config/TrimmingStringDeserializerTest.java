package org.conference.registration.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

class TrimmingStringDeserializerTest {

  record Sample(String name, List<String> tags) {}

  private final ObjectMapper mapper = mapper();

  private static ObjectMapper mapper() {
    Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
    new CoreConfig().trimmingStringDeserializer().customize(builder);
    return builder.build();
  }

  @Test
  void trimsLeadingAndTrailingWhitespace() throws Exception {
    Sample s = mapper.readValue("{\"name\":\"  Ana \\t\",\"tags\":[\" a \"]}", Sample.class);
    assertThat(s.name()).isEqualTo("Ana");
    assertThat(s.tags()).containsExactly("a");
  }

  @Test
  void whitespaceOnlyBecomesEmpty() throws Exception {
    assertThat(mapper.readValue("{\"name\":\"   \"}", Sample.class).name()).isEmpty();
  }

  @Test
  void normalisesToNfcAndKeepsSlovenianCharacters() throws Exception {
    // "Č" written as C + combining caron (NFD) must become the single NFC code point.
    Sample s = mapper.readValue("{\"name\":\"C\\u030Ceh Žiga\"}", Sample.class);
    assertThat(s.name()).isEqualTo("Čeh Žiga");
  }

  @Test
  void nullStaysNull() throws Exception {
    assertThat(mapper.readValue("{\"name\":null}", Sample.class).name()).isNull();
  }
}

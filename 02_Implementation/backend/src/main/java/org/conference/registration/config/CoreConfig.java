package org.conference.registration.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdScalarDeserializer;
import com.fasterxml.jackson.databind.deser.std.StringDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import java.io.IOException;
import java.text.Normalizer;
import java.time.Clock;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/** Clock, async execution and JSON input normalisation. */
@Configuration
@EnableAsync
public class CoreConfig {

  @Bean
  public Clock clock() {
    return Clock.systemUTC();
  }

  /**
   * Every incoming JSON string is stripped of leading/trailing whitespace and NFC-normalised, so
   * whitespace is not significant and whitespace-only values fail {@code @NotBlank}.
   */
  @Bean
  public Jackson2ObjectMapperBuilderCustomizer trimmingStringDeserializer() {
    SimpleModule module = new SimpleModule("trimming-strings");
    module.addDeserializer(String.class, new TrimmingStringDeserializer());
    return builder -> builder.modulesToInstall(module);
  }

  static final class TrimmingStringDeserializer extends StdScalarDeserializer<String> {
    private static final long serialVersionUID = 1L;

    TrimmingStringDeserializer() {
      super(String.class);
    }

    @Override
    public String deserialize(JsonParser parser, DeserializationContext context)
        throws IOException {
      String value = StringDeserializer.instance.deserialize(parser, context);
      return value == null ? null : Normalizer.normalize(value.strip(), Normalizer.Form.NFC);
    }
  }
}

package org.example.conference.shared.config;

import com.fasterxml.jackson.databind.module.SimpleModule;
import org.example.conference.shared.text.TrimmingStringDeserializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Registers inbound trimming of Unicode whitespace for all JSON strings. */
@Configuration(proxyBeanMethods = false)
public class JacksonConfig {

  @Bean
  public SimpleModule trimmingModule() {
    SimpleModule module = new SimpleModule("trimming-strings");
    module.addDeserializer(String.class, new TrimmingStringDeserializer());
    return module;
  }
}

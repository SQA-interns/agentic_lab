package org.example.conference.shared.text;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.jdk.StringDeserializer;

/** Applies {@link TextNormalizer#trim(String)} to every inbound JSON string. */
public class TrimmingStringDeserializer extends StringDeserializer {

  private static final long serialVersionUID = 1L;

  @Override
  public String deserialize(JsonParser parser, DeserializationContext context)
      throws JacksonException {
    return TextNormalizer.trim(super.deserialize(parser, context));
  }
}

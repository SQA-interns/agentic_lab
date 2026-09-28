package org.example.conference.shared.text;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StringDeserializer;
import java.io.IOException;

/** Applies {@link TextNormalizer#trim(String)} to every inbound JSON string. */
public class TrimmingStringDeserializer extends StringDeserializer {

  private static final long serialVersionUID = 1L;

  @Override
  public String deserialize(JsonParser parser, DeserializationContext context) throws IOException {
    return TextNormalizer.trim(super.deserialize(parser, context));
  }
}

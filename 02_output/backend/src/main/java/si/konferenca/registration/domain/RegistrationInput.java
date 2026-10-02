package si.konferenca.registration.domain;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A submitted registration before validation. A value is null when it was not submitted. Fields
 * named in {@code malformedFields} already have an error in {@code shapeErrors} (wrong kind of
 * value) and are not validated again.
 */
public record RegistrationInput(
    RegistrationType type,
    Map<TextField, String> texts,
    List<String> optionIds,
    Boolean consent,
    String captchaToken,
    List<FieldError> shapeErrors,
    Set<String> malformedFields) {

  public RegistrationInput {
    Map<TextField, String> copy = new EnumMap<>(TextField.class);
    copy.putAll(texts);
    texts = Collections.unmodifiableMap(copy);
    optionIds = optionIds == null ? null : List.copyOf(optionIds);
    shapeErrors = List.copyOf(shapeErrors);
    malformedFields = Set.copyOf(malformedFields);
  }
}

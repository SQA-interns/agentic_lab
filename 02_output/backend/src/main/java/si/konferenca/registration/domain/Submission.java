package si.konferenca.registration.domain;

import java.util.List;
import java.util.Map;

/**
 * A registration as submitted, before validation. Field values are raw; a missing field is absent.
 */
public record Submission(
    RegistrationType type,
    Map<Field, String> values,
    List<String> optionIds,
    List<String> consentIds) {

  public Submission {
    values = Map.copyOf(values);
    optionIds = List.copyOf(optionIds);
    consentIds = List.copyOf(consentIds);
  }
}

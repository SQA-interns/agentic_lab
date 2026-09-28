package si.konferenca.registration.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import si.konferenca.registration.domain.RegistrationType;

/**
 * Command to register a participant. Text values are already trimmed and syntactically validated by
 * the API layer. Type-specific fields not belonging to {@code type} are {@code null}.
 */
public record NewRegistration(
    RegistrationType type,
    String firstName,
    String lastName,
    String email,
    String organization,
    String studyInstitution,
    String studyProgramme,
    String studentId,
    List<String> optionIds,
    Map<String, Boolean> consents,
    String captchaToken,
    String clientIp) {

  public NewRegistration {
    optionIds =
        optionIds == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(optionIds));
    consents = consents == null ? Map.of() : Collections.unmodifiableMap(new HashMap<>(consents));
  }
}

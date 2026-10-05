package si.konferenca.registration.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import si.konferenca.registration.domain.OptionCategory;
import si.konferenca.registration.domain.Participant;
import si.konferenca.registration.domain.Registration;
import si.konferenca.registration.domain.RegistrationType;
import si.konferenca.registration.domain.SelectedOption;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class RegistrationJsonAndFormConfigTest {

  private final JsonMapper mapper = JsonMapper.builder().build();

  @Test
  void studentCopyHasOnlyStudentFieldsAndSchemaOrder() {
    UUID id = UUID.fromString("0b9a3c4e-6a8f-4f3e-9d43-2a1f6c5e7b10");
    Registration registration =
        new Registration(
            id,
            RegistrationType.STUDENT,
            new Participant("Žiga", "Č", "z@e.si", null, "UL", "FRI", "63"),
            List.of(new SelectedOption("meal-b", "Kosilo", OptionCategory.MEAL)),
            "c1",
            "text",
            Instant.parse("2026-10-05T10:00:00.123Z"));

    String text =
        new String(
            new RegistrationJson(mapper).toJson(registration),
            java.nio.charset.StandardCharsets.UTF_8);
    JsonNode copy = mapper.readTree(text);

    assertThat(text).startsWith("{\"schemaVersion\":1,\"id\":\"" + id + "\",\"type\":\"STUDENT\"");
    assertThat(copy.get("acceptedAt").asString()).isEqualTo("2026-10-05T10:00:00.123Z");
    assertThat(copy.get("participant").propertyNames())
        .containsExactly(
            "firstName", "lastName", "email", "studyInstitution", "studyProgramme", "studentId");
    assertThat(copy.get("options").get(0).get("category").asString()).isEqualTo("meal");
    assertThat(copy.get("consent").get("givenAt").asString()).isEqualTo("2026-10-05T10:00:00.123Z");
  }

  @Test
  void formConfigHidesInactiveOptionsAndTheSiteKeyInTestMode() {
    ConferenceOptions options = RegistrationValidatorTest.OPTIONS;

    var test =
        new FormConfigService(() -> options, new PublicSettings("Konf", true, "site")).formConfig();
    var live =
        new FormConfigService(() -> options, new PublicSettings("Konf", false, "site"))
            .formConfig();

    assertThat(test.activeOptions())
        .extracting(ConferenceOptions.Option::id)
        .containsExactly("ws-a", "meal-b");
    assertThat(test.captchaSiteKey()).isNull();
    assertThat(test.captchaTestMode()).isTrue();
    assertThat(live.captchaSiteKey()).isEqualTo("site");
    assertThat(live.conferenceName()).isEqualTo("Konf");
    assertThat(live.consent().text()).isEqualTo("Consent text");
  }

  @Test
  void normalizedEmailIsTrimmedAndLowerCased() {
    assertThat(Registration.normalizeEmail("  Ana.NOVAK@Example.SI "))
        .isEqualTo("ana.novak@example.si");
  }
}

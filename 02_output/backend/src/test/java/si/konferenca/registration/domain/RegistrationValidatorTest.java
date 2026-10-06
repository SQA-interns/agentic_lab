package si.konferenca.registration.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RegistrationValidatorTest {

  private static final ConferenceCatalog CATALOG =
      new ConferenceCatalog(
          List.of(
              new ConferenceOption(
                  "ws-a",
                  "Workshop A",
                  OptionCategory.WORKSHOP,
                  true,
                  EnumSet.allOf(RegistrationType.class)),
              new ConferenceOption(
                  "ws-ext",
                  "External only",
                  OptionCategory.WORKSHOP,
                  true,
                  EnumSet.of(RegistrationType.EXTERNAL)),
              new ConferenceOption(
                  "ws-old",
                  "Old",
                  OptionCategory.WORKSHOP,
                  false,
                  EnumSet.allOf(RegistrationType.class)),
              new ConferenceOption(
                  "meal",
                  "Lunch",
                  OptionCategory.MEAL,
                  true,
                  EnumSet.allOf(RegistrationType.class))),
          List.of(
              new ConsentDefinition("data", "Data processing", true),
              new ConsentDefinition("photo", "Photos", false)));

  private final RegistrationValidator validator = new RegistrationValidator(CATALOG);

  private static RegistrationSubmission external(
      String firstName, List<String> options, List<String> consents) {
    return new RegistrationSubmission(
        "external",
        firstName,
        "Novak",
        "ana@example.com",
        "IJS",
        null,
        null,
        null,
        options,
        consents,
        "token");
  }

  private static RegistrationSubmission validExternal() {
    return external("Ana", List.of("ws-a", "meal"), List.of("data"));
  }

  private static RegistrationSubmission validStudent() {
    return new RegistrationSubmission(
        "student",
        "Luka",
        "Kranjc",
        "luka@example.com",
        null,
        "UM",
        "Informatika",
        "931",
        List.of("ws-a"),
        List.of("data", "photo"),
        "token");
  }

  private List<String> errors(RegistrationSubmission submission) {
    return validator.validate(submission).errors().stream()
        .map(e -> e.field() + ":" + e.code())
        .toList();
  }

  @Test
  void validExternalRegistrationIsAcceptedWithStrippedValuesInCatalogOrder() {
    RegistrationValidator.Result result =
        validator.validate(
            new RegistrationSubmission(
                "external",
                " Ana ",
                "Novak\t",
                " ana@example.com ",
                " IJS ",
                "",
                " ",
                null,
                List.of("meal", "ws-a"),
                List.of("data"),
                "t"));

    assertThat(result.isValid()).isTrue();
    assertThat(result.errors()).isEmpty();
    RegistrationValidator.ValidRegistration valid = result.registration().orElseThrow();
    assertThat(valid.type()).isEqualTo(RegistrationType.EXTERNAL);
    assertThat(valid.participant())
        .isEqualTo(new Participant("Ana", "Novak", "ana@example.com", "IJS", null, null, null));
    assertThat(valid.options()).extracting(ConferenceOption::id).containsExactly("meal", "ws-a");
    assertThat(valid.consents()).extracting(ConsentDefinition::id).containsExactly("data");
  }

  @Test
  void validStudentRegistrationKeepsStudyFieldsAndOptionalConsent() {
    RegistrationValidator.Result result = validator.validate(validStudent());

    assertThat(result.isValid()).isTrue();
    RegistrationValidator.ValidRegistration valid = result.registration().orElseThrow();
    assertThat(valid.participant())
        .isEqualTo(
            new Participant(
                "Luka", "Kranjc", "luka@example.com", null, "UM", "Informatika", "931"));
    assertThat(valid.consents()).extracting(ConsentDefinition::id).containsExactly("data", "photo");
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "EXTERNAL", "guest"})
  void unknownOrMissingTypeIsRequired(String type) {
    RegistrationSubmission s =
        new RegistrationSubmission(
            type,
            "Ana",
            "Novak",
            "a@b.si",
            "IJS",
            null,
            null,
            null,
            List.of(),
            List.of("data"),
            "t");

    assertThat(errors(s)).containsExactly("type:REQUIRED");
  }

  @Test
  void nullTypeIsRequiredAndOptionAudienceIsNotChecked() {
    RegistrationSubmission s =
        new RegistrationSubmission(
            null,
            "Ana",
            "Novak",
            "a@b.si",
            null,
            null,
            null,
            null,
            List.of("ws-ext"),
            List.of("data"),
            "t");

    assertThat(errors(s)).containsExactly("type:REQUIRED");
  }

  @Test
  void controlCharactersInsideAValueAreRejected() {
    assertThat(errors(external("An\na", List.of(), List.of("data"))))
        .containsExactly("firstName:INVALID_CHARACTERS");
    assertThat(errors(external("An\u0000a", List.of(), List.of("data"))))
        .containsExactly("firstName:INVALID_CHARACTERS");
  }

  @Test
  void lettersOfAnyScriptAreAllowed() {
    assertThat(errors(external("Žiga Čebašek Ђорђе 李", List.of(), List.of("data")))).isEmpty();
  }

  @Test
  void valuesAtTheLimitPassAndOneCharacterMoreIsTooLong() {
    assertThat(errors(external("a".repeat(100), List.of(), List.of("data")))).isEmpty();
    assertThat(errors(external("a".repeat(101), List.of(), List.of("data"))))
        .containsExactly("firstName:TOO_LONG");
  }

  @Test
  void studentIdAndInstitutionLimits() {
    RegistrationSubmission tooLong =
        new RegistrationSubmission(
            "student",
            "L",
            "K",
            "l@k.si",
            null,
            "u".repeat(201),
            "p".repeat(200),
            "1".repeat(51),
            List.of(),
            List.of("data"),
            "t");

    assertThat(errors(tooLong)).containsExactly("studyInstitution:TOO_LONG", "studentId:TOO_LONG");
  }

  @Test
  void emailOverMaximumLengthIsTooLong() {
    String email = "a".repeat(250) + "@b.si";
    RegistrationSubmission s =
        new RegistrationSubmission(
            "external", "A", "N", email, "IJS", null, null, null, List.of(), List.of("data"), "t");

    assertThat(errors(s)).containsExactly("email:TOO_LONG");
  }

  @Test
  void studentRejectsOrganizationButAcceptsItEmpty() {
    RegistrationSubmission withOrganization =
        new RegistrationSubmission(
            "student", "L", "K", "l@k.si", "IJS", "UM", "P", "1", List.of(), List.of("data"), "t");
    RegistrationSubmission withBlankOrganization =
        new RegistrationSubmission(
            "student", "L", "K", "l@k.si", "  ", "UM", "P", "1", List.of(), List.of("data"), "t");

    assertThat(errors(withOrganization)).containsExactly("organization:NOT_ALLOWED");
    assertThat(errors(withBlankOrganization)).isEmpty();
  }

  @Test
  void externalRejectsEachStudyField() {
    RegistrationSubmission s =
        new RegistrationSubmission(
            "external", "A", "N", "a@b.si", "IJS", "UM", "P", "1", List.of(), List.of("data"), "t");

    assertThat(errors(s))
        .containsExactly(
            "studyInstitution:NOT_ALLOWED", "studyProgramme:NOT_ALLOWED", "studentId:NOT_ALLOWED");
  }

  @Test
  void eachOptionProblemIsReportedOnceByCode() {
    assertThat(
            errors(
                external(
                    "Ana",
                    Arrays.asList("ws-a", "ws-a", "ws-a", "nope", "other", "ws-old", null),
                    List.of("data"))))
        .containsExactly(
            "optionIds:DUPLICATE_OPTION", "optionIds:UNKNOWN_OPTION", "optionIds:INACTIVE_OPTION");
  }

  @Test
  void studentCannotSelectExternalOnlyOption() {
    RegistrationSubmission s =
        new RegistrationSubmission(
            "student",
            "L",
            "K",
            "l@k.si",
            null,
            "UM",
            "P",
            "1",
            List.of("ws-ext"),
            List.of("data"),
            "t");

    assertThat(errors(s)).containsExactly("optionIds:OPTION_NOT_OFFERED");
    assertThat(errors(external("Ana", List.of("ws-ext"), List.of("data")))).isEmpty();
  }

  @Test
  void unknownOrNullConsentIsRejectedAndRepeatedConsentCountsOnce() {
    assertThat(errors(external("Ana", List.of(), List.of("data", "marketing"))))
        .containsExactly("consents:UNKNOWN_CONSENT");
    assertThat(errors(external("Ana", List.of(), Arrays.asList("data", null))))
        .containsExactly("consents:UNKNOWN_CONSENT");
    RegistrationValidator.Result repeated =
        validator.validate(external("Ana", List.of(), List.of("data", "data")));
    assertThat(repeated.registration().orElseThrow().consents()).hasSize(1);
  }

  @Test
  void missingMandatoryConsentNamesThatConsent() {
    assertThat(errors(external("Ana", List.of(), List.of("photo"))))
        .containsExactly("consents.data:CONSENT_REQUIRED");
  }

  @Test
  void captchaTokenIsRequiredAndBounded() {
    RegistrationSubmission blank =
        new RegistrationSubmission(
            "external", "A", "N", "a@b.si", "IJS", null, null, null, null, List.of("data"), " ");
    RegistrationSubmission tooLong =
        new RegistrationSubmission(
            "external",
            "A",
            "N",
            "a@b.si",
            "IJS",
            null,
            null,
            null,
            null,
            List.of("data"),
            "t".repeat(4097));

    assertThat(errors(blank)).containsExactly("captchaToken:REQUIRED");
    assertThat(errors(tooLong)).containsExactly("captchaToken:TOO_LONG");
  }

  @Test
  void missingListsMeanNoOptionsAndNoConsents() {
    RegistrationSubmission s =
        new RegistrationSubmission(
            "external", "A", "N", "a@b.si", "IJS", null, null, null, null, null, "t");

    assertThat(errors(s)).containsExactly("consents.data:CONSENT_REQUIRED");
  }

  @Test
  void allProblemsAreReportedTogether() {
    RegistrationSubmission s =
        new RegistrationSubmission(
            "external", "", "", "x", "", null, null, null, List.of("nope"), List.of(), "");

    assertThat(errors(s))
        .containsExactly(
            "firstName:REQUIRED",
            "lastName:REQUIRED",
            "email:INVALID_EMAIL",
            "organization:REQUIRED",
            "optionIds:UNKNOWN_OPTION",
            "consents.data:CONSENT_REQUIRED",
            "captchaToken:REQUIRED");
    assertThat(validator.validate(s).isValid()).isFalse();
    assertThat(validator.validate(s).registration()).isEmpty();
  }

  @Test
  void controlCharacterDetection() {
    assertThat(RegistrationValidator.hasControlCharacter("a\tb")).isTrue();
    assertThat(RegistrationValidator.hasControlCharacter("\u007f")).isTrue();
    assertThat(RegistrationValidator.hasControlCharacter("čšž ‐ é")).isFalse();
  }
}

package si.konferenca.registration.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RegistrationValidatorTest {

  private static final ConsentDefinition MANDATORY =
      new ConsentDefinition("data-processing", "I agree.", true);
  private static final ConsentDefinition OPTIONAL =
      new ConsentDefinition("photos", "Photos.", false);

  private static final OptionsCatalog CATALOG =
      new OptionsCatalog(
          List.of(
              option("ws-a", Category.WORKSHOP, true, RegistrationType.values()),
              option("ws-off", Category.WORKSHOP, false, RegistrationType.values()),
              option("ev-ext", Category.EVENT, true, RegistrationType.EXTERNAL),
              option("ev-stu", Category.EVENT, true, RegistrationType.STUDENT),
              option("meal-1", Category.MEAL, true, RegistrationType.values()),
              option("meal-2", Category.MEAL, true, RegistrationType.values()),
              option("meal-3", Category.MEAL, true, RegistrationType.values())),
          Map.of(Category.MEAL, 2),
          List.of(MANDATORY, OPTIONAL));

  private final RegistrationValidator validator = new RegistrationValidator(CATALOG);

  private static ConferenceOption option(
      String id, Category category, boolean active, RegistrationType... types) {
    return new ConferenceOption(
        id, id + " name", category, active, EnumSet.copyOf(Arrays.asList(types)));
  }

  private static RegistrationSubmission external(
      String firstName, String email, List<String> options, List<String> consents) {
    return new RegistrationSubmission(
        "EXTERNAL", firstName, "Novak", email, "IJS", null, null, null, options, consents, "tok");
  }

  private static RegistrationSubmission validExternal() {
    return external("Ana", "ana@example.si", List.of("ws-a"), List.of("data-processing"));
  }

  private static RegistrationSubmission validStudent() {
    return new RegistrationSubmission(
        "STUDENT",
        "Luka",
        "Kovač",
        "luka@example.si",
        "",
        "UL",
        "RI",
        "6320",
        List.of("ev-stu"),
        List.of("data-processing"),
        "tok");
  }

  private List<String> errorFields(RegistrationSubmission s) {
    return validator.validate(s).errors().stream().map(FieldError::field).toList();
  }

  @Test
  void acceptsValidExternalAndTrimsText() {
    ValidationResult r =
        validator.validate(
            external("  Ana ", " ana@example.si ", List.of("ws-a"), List.of("data-processing")));

    assertThat(r.valid()).isTrue();
    assertThat(r.participant().firstName()).isEqualTo("Ana");
    assertThat(r.participant().email()).isEqualTo("ana@example.si");
    assertThat(r.participant().type()).isEqualTo(RegistrationType.EXTERNAL);
    assertThat(r.participant().studentId()).isNull();
    assertThat(r.options()).extracting(ConferenceOption::id).containsExactly("ws-a");
    assertThat(r.consents()).containsExactly(MANDATORY);
  }

  @Test
  void acceptsValidStudentWithBlankOrganization() {
    ValidationResult r = validator.validate(validStudent());

    assertThat(r.valid()).isTrue();
    assertThat(r.participant().organization()).isNull();
    assertThat(r.participant().studyInstitution()).isEqualTo("UL");
  }

  @Test
  void rejectsUnknownType() {
    RegistrationSubmission s =
        new RegistrationSubmission(
            "VIP", "A", "B", "a@b.si", "X", null, null, null, List.of(), List.of(), "t");

    assertThat(errorFields(s)).contains("type");
  }

  @Test
  void acceptsLowerCaseType() {
    assertThat(RegistrationType.parse(" student ")).contains(RegistrationType.STUDENT);
    assertThat(RegistrationType.parse(null)).isEmpty();
  }

  @Test
  void collectsEveryFieldError() {
    RegistrationSubmission s =
        new RegistrationSubmission(
            "STUDENT", " ", null, "bad", "Org", "", " ", null, null, null, " ");

    assertThat(errorFields(s))
        .containsExactlyInAnyOrder(
            "firstName",
            "lastName",
            "email",
            "studyInstitution",
            "studyProgramme",
            "studentId",
            "organization",
            "consentIds",
            "captchaToken");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"ana", "ana@", "@example.si", "ana novak@example.si", "ana@example", "a@@b.si"})
  void rejectsInvalidEmail(String email) {
    assertThat(errorFields(external("Ana", email, List.of(), List.of("data-processing"))))
        .containsExactly("email");
  }

  @Test
  void rejectsTooLongText() {
    assertThat(
            errorFields(
                external("x".repeat(201), "ana@example.si", List.of(), List.of("data-processing"))))
        .containsExactly("firstName");
    assertThat(
            errorFields(
                external("x".repeat(200), "ana@example.si", List.of(), List.of("data-processing"))))
        .isEmpty();
  }

  @Test
  void rejectsTooLongEmail() {
    String email = "a".repeat(245) + "@example.si";
    assertThat(errorFields(external("Ana", email, List.of(), List.of("data-processing"))))
        .containsExactly("email");
  }

  @ParameterizedTest
  @ValueSource(strings = {"Ana\nB", "Ana\r\nBcc: x@y.si", "Ana\u0000"})
  void rejectsControlCharacters(String name) {
    assertThat(errorFields(external(name, "ana@example.si", List.of(), List.of("data-processing"))))
        .containsExactly("firstName");
  }

  @Test
  void acceptsUnicodeLetters() {
    assertThat(
            errorFields(
                external(
                    "Špela Čučnik-Žagar", "s@example.si", List.of(), List.of("data-processing"))))
        .isEmpty();
  }

  @Test
  void rejectsInactiveUnknownDuplicateOrUnavailableOptions() {
    for (List<String> options :
        List.of(
            List.of("ws-off"),
            List.of("nope"),
            List.of("ws-a", "ws-a"),
            List.of("ev-stu"),
            Arrays.asList("ws-a", null))) {
      assertThat(errorFields(external("Ana", "a@example.si", options, List.of("data-processing"))))
          .as(options.toString())
          .containsExactly("optionIds");
    }
  }

  @Test
  void enforcesCategoryLimitExactly() {
    assertThat(
            errorFields(
                external(
                    "Ana",
                    "a@example.si",
                    List.of("meal-1", "meal-2"),
                    List.of("data-processing"))))
        .isEmpty();
    assertThat(
            errorFields(
                external(
                    "Ana",
                    "a@example.si",
                    List.of("meal-1", "meal-2", "meal-3"),
                    List.of("data-processing"))))
        .containsExactly("optionIds");
  }

  @Test
  void consentRules() {
    assertThat(errorFields(external("Ana", "a@example.si", List.of(), List.of())))
        .containsExactly("consentIds");
    assertThat(errorFields(external("Ana", "a@example.si", List.of(), List.of("photos"))))
        .containsExactly("consentIds");
    assertThat(
            errorFields(
                external("Ana", "a@example.si", List.of(), List.of("data-processing", "x"))))
        .containsExactly("consentIds");
    assertThat(
            errorFields(
                external(
                    "Ana",
                    "a@example.si",
                    List.of(),
                    List.of("data-processing", "data-processing"))))
        .containsExactly("consentIds");
    ValidationResult both =
        validator.validate(
            external("Ana", "a@example.si", List.of(), List.of("photos", "data-processing")));
    assertThat(both.consents()).containsExactly(OPTIONAL, MANDATORY);
  }

  @Test
  void rejectsFieldsOfTheOtherType() {
    RegistrationSubmission s =
        new RegistrationSubmission(
            "EXTERNAL",
            "Ana",
            "Novak",
            "a@example.si",
            "IJS",
            "UL",
            " ",
            "123",
            List.of(),
            List.of("data-processing"),
            "t");

    assertThat(errorFields(s)).containsExactlyInAnyOrder("studyInstitution", "studentId");

    RegistrationSubmission programmeOnly =
        new RegistrationSubmission(
            "EXTERNAL",
            "Ana",
            "Novak",
            "a@example.si",
            "IJS",
            null,
            "RI",
            null,
            List.of(),
            List.of("data-processing"),
            "t");
    assertThat(errorFields(programmeOnly)).containsExactly("studyProgramme");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"ana<b@example.si", "a,b@example.si", "a\"b@example.si", "a(b)@example.si"})
  void strictAddressParsingRejectsWhatThePatternLetsThrough(String email) {
    assertThat(errorFields(external("Ana", email, List.of(), List.of("data-processing"))))
        .containsExactly("email");
  }

  @Test
  void missingCaptchaTokenIsAFieldError() {
    RegistrationSubmission s =
        new RegistrationSubmission(
            "EXTERNAL",
            "Ana",
            "Novak",
            "a@example.si",
            "IJS",
            null,
            null,
            null,
            List.of(),
            List.of("data-processing"),
            null);

    assertThat(errorFields(s)).containsExactly("captchaToken");
    assertThat(validator.validate(validExternal()).valid()).isTrue();
  }
}

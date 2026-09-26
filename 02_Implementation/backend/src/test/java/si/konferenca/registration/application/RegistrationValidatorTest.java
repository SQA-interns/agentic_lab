package si.konferenca.registration.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import si.konferenca.registration.TestFixtures;
import si.konferenca.registration.domain.RegistrationType;

class RegistrationValidatorTest {

  private static ValidatorFactory factory;
  private static RegistrationValidator validator;

  @BeforeAll
  static void setUp() {
    factory = Validation.buildDefaultValidatorFactory();
    validator =
        new RegistrationValidator(
            factory.getValidator(),
            new ConferenceCatalog(TestFixtures.properties(TestFixtures.conference())));
  }

  @AfterAll
  static void tearDown() {
    factory.close();
  }

  static RegistrationCommand external(
      String first, String last, String email, String org, List<String> options) {
    return new RegistrationCommand(
        RegistrationType.EXTERNAL,
        first,
        last,
        email,
        org,
        null,
        null,
        null,
        options,
        List.of("privacy"),
        "token");
  }

  static RegistrationCommand student(
      String institution, String programme, String studentId, List<String> consents) {
    return new RegistrationCommand(
        RegistrationType.STUDENT,
        "Ana",
        "Novak",
        "ana@example.si",
        null,
        institution,
        programme,
        studentId,
        List.of(),
        consents,
        "token");
  }

  private static Map<String, String> errorsOf(RegistrationCommand command) {
    try {
      validator.validate(command);
    } catch (ValidationException e) {
      return e.getViolations().stream()
          .collect(Collectors.toMap(FieldViolation::field, FieldViolation::message));
    }
    return Map.of();
  }

  @Test
  void acceptsValidExternalRegistration() {
    NormalizedRegistration result =
        validator.validate(
            external("Janez", "Novak", "janez@example.si", "ACME", List.of("ws-a", "meal-lunch")));
    assertThat(result.type()).isEqualTo(RegistrationType.EXTERNAL);
    assertThat(result.optionIds()).containsExactly("ws-a", "meal-lunch");
  }

  @Test
  void acceptsValidStudentRegistration() {
    NormalizedRegistration result =
        validator.validate(student("FRI", "Informatika", "63200001", List.of("privacy")));
    assertThat(result.studentId()).isEqualTo("63200001");
  }

  @Test
  void acceptsRegistrationWithoutOptions() {
    assertThat(errorsOf(external("Janez", "Novak", "j@example.si", "ACME", List.of()))).isEmpty();
    assertThat(errorsOf(external("Janez", "Novak", "j@example.si", "ACME", null))).isEmpty();
  }

  @Test
  void trimsLeadingAndTrailingWhitespace() {
    NormalizedRegistration result =
        validator.validate(
            external("  Janez ", "\tNovak\u00a0", " j@example.si ", " ACME ", List.of(" ws-a ")));
    assertThat(result.firstName()).isEqualTo("Janez");
    assertThat(result.lastName()).isEqualTo("Novak");
    assertThat(result.email()).isEqualTo("j@example.si");
    assertThat(result.organization()).isEqualTo("ACME");
    assertThat(result.optionIds()).containsExactly("ws-a");
  }

  @Test
  void preservesSlovenianCharacters() {
    NormalizedRegistration result =
        validator.validate(
            external("Žiga", "Šušteršič", "ziga@example.si", "Univerza v Ljubljani — FRI", null));
    assertThat(result.firstName()).isEqualTo("Žiga");
    assertThat(result.lastName()).isEqualTo("Šušteršič");
    assertThat(result.organization()).isEqualTo("Univerza v Ljubljani — FRI");
  }

  @Test
  void reportsEveryMissingExternalField() {
    Map<String, String> errors = errorsOf(external(null, "", "  ", null, List.of()));
    assertThat(errors)
        .containsEntry("firstName", "This field is required.")
        .containsEntry("lastName", "This field is required.")
        .containsEntry("email", "This field is required.")
        .containsEntry("organization", "This field is required.");
  }

  @Test
  void treatsWhitespaceOnlyValueAsMissing() {
    assertThat(errorsOf(external("   ", "Novak", "j@example.si", "ACME", List.of())))
        .containsOnlyKeys("firstName");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "plainaddress",
        "missing-at.example.si",
        "a@b",
        "a@@example.si",
        "a b@example.si",
        "@example.si",
        "janez@",
        "janez@example..si",
        "janez@-example.si"
      })
  void rejectsInvalidEmail(String email) {
    assertThat(errorsOf(external("Janez", "Novak", email, "ACME", List.of())))
        .containsEntry("email", "Enter a valid email address.");
  }

  @ParameterizedTest
  @ValueSource(strings = {"janez.novak@example.si", "a+tag@sub.example.com", "x_y@ex-ample.org"})
  void acceptsValidEmail(String email) {
    assertThat(errorsOf(external("Janez", "Novak", email, "ACME", List.of()))).isEmpty();
  }

  @Test
  void rejectsUnknownOption() {
    assertThat(errorsOf(external("Janez", "Novak", "j@example.si", "ACME", List.of("nope"))))
        .containsEntry("optionIds", "Contains an unknown or unavailable option.");
  }

  @Test
  void rejectsInactiveOption() {
    assertThat(errorsOf(external("Janez", "Novak", "j@example.si", "ACME", List.of("ws-old"))))
        .containsEntry("optionIds", "Contains an unknown or unavailable option.");
  }

  @Test
  void rejectsNullOptionEntry() {
    java.util.ArrayList<String> options = new java.util.ArrayList<>();
    options.add(null);
    assertThat(errorsOf(external("Janez", "Novak", "j@example.si", "ACME", options)))
        .containsKey("optionIds");
  }

  @Test
  void rejectsTooManyOptions() {
    List<String> options =
        java.util.stream.IntStream.range(0, 51).mapToObj(i -> "opt-" + i).toList();
    assertThat(errorsOf(external("Janez", "Novak", "j@example.si", "ACME", options)))
        .containsEntry("optionIds", "Too many options selected.");
  }

  @Test
  void rejectsMissingRequiredConsent() {
    assertThat(errorsOf(student("FRI", "Informatika", "63200001", List.of())))
        .containsEntry("consents.privacy", "This consent is required.");
  }

  @Test
  void optionalConsentIsNotRequired() {
    assertThat(errorsOf(student("FRI", "Informatika", "63200001", List.of("privacy")))).isEmpty();
    assertThat(errorsOf(student("FRI", "Informatika", "63200001", List.of("privacy", "photos"))))
        .isEmpty();
  }

  @Test
  void rejectsUnknownConsent() {
    assertThat(errorsOf(student("FRI", "Informatika", "63200001", List.of("privacy", "x"))))
        .containsEntry("consentIds", "Contains an unknown consent.");
  }

  @Test
  void reportsEveryMissingStudentField() {
    assertThat(errorsOf(student(null, " ", "", List.of("privacy"))))
        .containsOnlyKeys("studyInstitution", "studyProgramme", "studentId");
  }

  @Test
  void studentDoesNotRequireOrganization() {
    assertThat(errorsOf(student("FRI", "Informatika", "63200001", List.of("privacy")))).isEmpty();
  }

  @Test
  void externalDoesNotRequireStudentFields() {
    assertThat(errorsOf(external("Janez", "Novak", "j@example.si", "ACME", List.of()))).isEmpty();
  }

  @Test
  void rejectsFieldsOfTheOtherRegistrationType() {
    RegistrationCommand command =
        new RegistrationCommand(
            RegistrationType.EXTERNAL,
            "Janez",
            "Novak",
            "j@example.si",
            "ACME",
            "FRI",
            null,
            "123",
            List.of(),
            List.of("privacy"),
            "token");
    assertThat(errorsOf(command))
        .containsEntry("studyInstitution", "Not applicable to this registration type.")
        .containsEntry("studentId", "Not applicable to this registration type.");
  }

  @Test
  void rejectsMissingType() {
    RegistrationCommand command =
        new RegistrationCommand(
            null, "Janez", "Novak", "j@example.si", "ACME", null, null, null, null, null, "t");
    assertThat(errorsOf(command)).containsEntry("type", "This field is required.");
  }

  @Test
  void rejectsControlCharacters() {
    assertThat(errorsOf(external("Jan\u0000ez", "No\nvak", "j@example.si", "AC\u0085ME", null)))
        .containsEntry("firstName", "Contains characters that are not allowed.")
        .containsEntry("lastName", "Contains characters that are not allowed.")
        .containsEntry("organization", "Contains characters that are not allowed.");
  }

  @Test
  void rejectsOverlongValues() {
    assertThat(errorsOf(external("x".repeat(101), "Novak", "j@example.si", "y".repeat(201), null)))
        .containsEntry("firstName", "Must be at most 100 characters.")
        .containsEntry("organization", "Must be at most 200 characters.");
  }

  @Test
  void acceptsValuesAtMaximumLength() {
    assertThat(errorsOf(external("x".repeat(100), "Novak", "j@example.si", "y".repeat(200), null)))
        .isEmpty();
  }

  @Test
  void violationsAreOrderedLikeTheForm() {
    assertThatThrownBy(() -> validator.validate(external(null, null, null, null, List.of("nope"))))
        .isInstanceOfSatisfying(
            ValidationException.class,
            e ->
                assertThat(e.getViolations())
                    .extracting(FieldViolation::field)
                    .containsExactly(
                        "firstName", "lastName", "email", "organization", "optionIds"));
  }
}

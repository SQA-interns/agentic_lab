package si.konferenca.registration.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import si.konferenca.registration.domain.Category;
import si.konferenca.registration.domain.ConferenceOption;
import si.konferenca.registration.domain.ConsentDefinition;
import si.konferenca.registration.domain.OptionCatalogue;
import si.konferenca.registration.domain.RegistrationType;

class RegistrationValidatorTest {

  private static final OptionCatalogue CATALOGUE =
      new OptionCatalogue(
          Map.of(Category.WORKSHOP, 1, Category.MEAL, 2),
          List.of(
              option("ws-a", Category.WORKSHOP, true, RegistrationType.values()),
              option("ws-b", Category.WORKSHOP, true, RegistrationType.values()),
              option("ws-old", Category.WORKSHOP, false, RegistrationType.values()),
              option("meal-1", Category.MEAL, true, RegistrationType.values()),
              option("meal-2", Category.MEAL, true, RegistrationType.values()),
              option("meal-3", Category.MEAL, true, RegistrationType.values()),
              option("ev-ext", Category.EVENT, true, RegistrationType.EXTERNAL)),
          List.of(
              new ConsentDefinition("data", "Data processing", true),
              new ConsentDefinition("photos", "Photos", false)));

  private final RegistrationValidator validator = new RegistrationValidator(CATALOGUE);

  private static ConferenceOption option(
      String id, Category category, boolean active, RegistrationType... types) {
    return new ConferenceOption(
        id, id + " name", category, active, EnumSet.copyOf(Arrays.asList(types)));
  }

  private static RegistrationCommand external(
      String firstName, String email, List<String> options) {
    return new RegistrationCommand(
        RegistrationType.EXTERNAL,
        firstName,
        "Novak",
        email,
        "IJS",
        null,
        null,
        null,
        options,
        List.of("data"),
        "token");
  }

  private static RegistrationCommand student(String studentId) {
    return new RegistrationCommand(
        RegistrationType.STUDENT,
        "Luka",
        "Kranjc",
        "luka@example.si",
        null,
        "FRI",
        "RI",
        studentId,
        List.of(),
        List.of("data"),
        "token");
  }

  private List<String> errors(RegistrationCommand command) {
    ValidationException e =
        catchThrowableOfType(ValidationException.class, () -> validator.validate(command));
    assertThat(e).as("validation should fail").isNotNull();
    return e.errors().stream().map(error -> error.field() + ":" + error.code()).toList();
  }

  @Test
  void validRegistrationIsTrimmedAndKeepsOnlyTypeFields() {
    RegistrationValidator.ValidRegistration valid =
        validator.validate(external("  Ana \t", " ana@example.si ", List.of("ws-a", "meal-1")));

    assertThat(valid.participant().firstName()).isEqualTo("Ana");
    assertThat(valid.participant().email()).isEqualTo("ana@example.si");
    assertThat(valid.participant().organization()).isEqualTo("IJS");
    assertThat(valid.participant().studentId()).isNull();
    assertThat(valid.options()).extracting(ConferenceOption::id).containsExactly("ws-a", "meal-1");
    assertThat(valid.consents()).extracting(ConsentDefinition::id).containsExactly("data");
  }

  @Test
  void studentRegistrationKeepsStudentFields() {
    RegistrationValidator.ValidRegistration valid = validator.validate(student(" 6320 "));

    assertThat(valid.participant().studentId()).isEqualTo("6320");
    assertThat(valid.participant().studyInstitution()).isEqualTo("FRI");
    assertThat(valid.participant().organization()).isNull();
  }

  @Test
  void fieldOfTheOtherTypeIsNotAllowed() {
    RegistrationCommand command =
        new RegistrationCommand(
            RegistrationType.STUDENT,
            "Luka",
            "Kranjc",
            "luka@example.si",
            "IJS",
            "FRI",
            "RI",
            "1",
            List.of(),
            List.of("data"),
            "t");

    assertThat(errors(command)).containsExactly("organization:not_allowed");
  }

  @Test
  void studentFieldsOnExternalAreNotAllowed() {
    RegistrationCommand command =
        new RegistrationCommand(
            RegistrationType.EXTERNAL,
            "Ana",
            "Novak",
            "ana@example.si",
            "IJS",
            "FRI",
            "",
            "1",
            List.of(),
            List.of("data"),
            "t");

    assertThat(errors(command))
        .containsExactlyInAnyOrder(
            "studyInstitution:not_allowed", "studyProgramme:not_allowed", "studentId:not_allowed");
  }

  @Test
  void everyErrorIsReportedAtOnce() {
    RegistrationCommand command =
        new RegistrationCommand(
            RegistrationType.EXTERNAL,
            null,
            "x".repeat(101),
            "bad",
            "Org\u0000",
            null,
            null,
            null,
            List.of("nope"),
            List.of(),
            "t");

    assertThat(errors(command))
        .containsExactlyInAnyOrder(
            "firstName:required",
            "lastName:too_long",
            "email:invalid_email",
            "organization:invalid_characters",
            "optionIds:unknown_option",
            "consentIds:consent_required");
  }

  @Test
  void lengthIsCountedInCodePointsNotUtf16Units() {
    String hundredEmoji = "😀".repeat(100);

    RegistrationValidator.ValidRegistration valid =
        validator.validate(external(hundredEmoji, "ana@example.si", List.of()));

    assertThat(valid.participant().firstName()).isEqualTo(hundredEmoji);
    assertThat(errors(external(hundredEmoji + "a", "ana@example.si", List.of())))
        .containsExactly("firstName:too_long");
  }

  @Test
  void requiredIsReportedBeforeOtherRulesOfTheSameField() {
    assertThat(errors(external("   ", "   ", List.of())))
        .containsExactlyInAnyOrder("firstName:required", "email:required");
  }

  @ParameterizedTest
  @ValueSource(strings = {"a@b", "a b@c.si", "a@@c.si", "@c.si", "a@.", "a@c."})
  void malformedEmailsAreRejected(String email) {
    assertThat(errors(external("Ana", email, List.of()))).containsExactly("email:invalid_email");
  }

  @Test
  void optionRulesReportUnknownInactiveUnavailableAndTooMany() {
    RegistrationCommand student =
        new RegistrationCommand(
            RegistrationType.STUDENT,
            "Luka",
            "Kranjc",
            "luka@example.si",
            null,
            "FRI",
            "RI",
            "1",
            List.of("ws-old", "ev-ext", "meal-1", "meal-2", "meal-3", "none"),
            List.of("data"),
            "t");

    assertThat(errors(student))
        .containsExactlyInAnyOrder(
            "optionIds:inactive_option",
            "optionIds:option_not_available",
            "optionIds:too_many_options",
            "optionIds:unknown_option");
  }

  @Test
  void duplicateOptionIdsCountOnce() {
    RegistrationValidator.ValidRegistration valid =
        validator.validate(external("Ana", "ana@example.si", List.of("ws-a", "ws-a")));

    assertThat(valid.options()).extracting(ConferenceOption::id).containsExactly("ws-a");
  }

  @Test
  void selectionsUpToTheCategoryLimitAreAccepted() {
    RegistrationValidator.ValidRegistration valid =
        validator.validate(external("Ana", "ana@example.si", List.of("meal-1", "meal-2")));

    assertThat(valid.options()).hasSize(2);
  }

  @Test
  void optionalConsentMayBeGivenAndUnknownConsentIsRejected() {
    RegistrationCommand withOptional =
        new RegistrationCommand(
            RegistrationType.EXTERNAL,
            "Ana",
            "Novak",
            "ana@example.si",
            "IJS",
            null,
            null,
            null,
            List.of(),
            List.of("data", "photos"),
            "t");
    assertThat(validator.validate(withOptional).consents())
        .extracting(ConsentDefinition::id)
        .containsExactly("data", "photos");

    RegistrationCommand unknown =
        new RegistrationCommand(
            RegistrationType.EXTERNAL,
            "Ana",
            "Novak",
            "ana@example.si",
            "IJS",
            null,
            null,
            null,
            List.of(),
            List.of("data", "other"),
            "t");
    assertThat(errors(unknown)).containsExactly("consentIds:unknown_consent");
  }

  @Test
  void messagesNameTheFieldLabelAndLimit() {
    ValidationException e =
        catchThrowableOfType(
            ValidationException.class,
            () -> validator.validate(external("x".repeat(101), "a@b.si", List.of("ws-a", "ws-b"))));

    assertThat(e.errors())
        .extracting(ValidationException.FieldError::message)
        .containsExactlyInAnyOrder(
            "First name must be at most 100 characters.", "Select at most 1 of workshops.");
  }
}

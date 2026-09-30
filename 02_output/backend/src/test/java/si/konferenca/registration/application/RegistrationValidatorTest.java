package si.konferenca.registration.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import si.konferenca.registration.application.RegistrationValidator.ValidRegistration;
import si.konferenca.registration.application.ValidationException.FieldViolation;
import si.konferenca.registration.domain.ConferenceOption;
import si.konferenca.registration.domain.ConsentDefinition;
import si.konferenca.registration.domain.OptionCategory;
import si.konferenca.registration.domain.RegistrationType;

class RegistrationValidatorTest {

  static final OptionCatalog CATALOG =
      new OptionCatalog() {
        final List<ConferenceOption> all =
            List.of(
                new ConferenceOption("ws", "Workshop", OptionCategory.WORKSHOP, true),
                new ConferenceOption("old", "Old", OptionCategory.WORKSHOP, false),
                new ConferenceOption("meal", "Meal", OptionCategory.MEAL, true));

        @Override
        public List<ConferenceOption> activeOptions() {
          return all.stream().filter(ConferenceOption::active).toList();
        }

        @Override
        public Optional<ConferenceOption> find(String id) {
          return all.stream().filter(o -> o.id().equals(id)).findFirst();
        }

        @Override
        public List<ConsentDefinition> consents() {
          return List.of(
              new ConsentDefinition("privacy", "Privacy", true),
              new ConsentDefinition("news", "News", false));
        }
      };

  private final RegistrationValidator validator = new RegistrationValidator(CATALOG);

  static RegistrationCommand external() {
    return new RegistrationCommand(
        "EXTERNAL",
        " Ana ",
        "Novak",
        "ana@example.si",
        "IJS",
        null,
        null,
        null,
        List.of("meal", "ws"),
        List.of("news", "privacy"),
        "token");
  }

  static RegistrationCommand student() {
    return new RegistrationCommand(
        "STUDENT",
        "Luka",
        "Kovač",
        "luka@example.si",
        null,
        "UL",
        "RI",
        "6321",
        List.of(),
        List.of("privacy"),
        "token");
  }

  private List<String> fieldsOf(RegistrationCommand c) {
    try {
      validator.validate(c);
      return List.of();
    } catch (ValidationException e) {
      return e.violations().stream().map(FieldViolation::field).toList();
    }
  }

  @Test
  void acceptsExternalAndTrims() {
    ValidRegistration valid = validator.validate(external());

    assertThat(valid.details().type()).isEqualTo(RegistrationType.EXTERNAL);
    assertThat(valid.details().firstName()).isEqualTo("Ana");
    assertThat(valid.details().lastName()).isEqualTo("Novak");
    assertThat(valid.details().email()).isEqualTo("ana@example.si");
    assertThat(valid.details().organization()).isEqualTo("IJS");
    assertThat(valid.details().studentId()).isNull();
    assertThat(valid.options()).extracting(ConferenceOption::id).containsExactly("meal", "ws");
    assertThat(valid.consentIds()).containsExactly("privacy", "news");
  }

  @Test
  void acceptsStudentWithoutOptions() {
    ValidRegistration valid = validator.validate(student());

    assertThat(valid.details().type()).isEqualTo(RegistrationType.STUDENT);
    assertThat(valid.details().studyInstitution()).isEqualTo("UL");
    assertThat(valid.details().studyProgramme()).isEqualTo("RI");
    assertThat(valid.details().studentId()).isEqualTo("6321");
    assertThat(valid.details().organization()).isNull();
    assertThat(valid.options()).isEmpty();
  }

  @Test
  void collectsAllErrorsTogether() {
    RegistrationCommand c =
        new RegistrationCommand(null, "", " ", "bad", null, null, null, null, null, null, null);

    assertThat(fieldsOf(c))
        .containsExactly(
            "type", "firstName", "lastName", "email", "optionIds", "consents", "recaptchaToken");
  }

  @ParameterizedTest
  @ValueSource(strings = {"VISITOR", "external", ""})
  void rejectsUnknownType(String type) {
    RegistrationCommand c = withType(external(), type);

    assertThat(fieldsOf(c)).containsExactly("type");
  }

  @Test
  void externalRequiresOrganizationAndForbidsStudentFields() {
    RegistrationCommand c =
        new RegistrationCommand(
            "EXTERNAL", "A", "B", "a@b.si", " ", "", "x", "1", List.of(), List.of("privacy"), "t");

    assertThat(fieldsOf(c))
        .containsExactly("organization", "studyInstitution", "studyProgramme", "studentId");
  }

  @Test
  void studentRequiresStudentFieldsAndForbidsOrganization() {
    RegistrationCommand c =
        new RegistrationCommand(
            "STUDENT",
            "A",
            "B",
            "a@b.si",
            "",
            null,
            null,
            null,
            List.of(),
            List.of("privacy"),
            "t");

    assertThat(fieldsOf(c))
        .containsExactly("studyInstitution", "studyProgramme", "studentId", "organization");
  }

  @Test
  void enforcesMaximumLengthsAfterTrimming() {
    List<FieldViolation> errors = new ArrayList<>();

    assertThat(RegistrationValidator.text("f", " " + "a".repeat(100) + " ", 100, errors))
        .hasSize(100);
    assertThat(errors).isEmpty();
    assertThat(RegistrationValidator.text("f", "a".repeat(101), 100, errors)).isNull();
    assertThat(errors)
        .extracting(FieldViolation::message)
        .containsExactly("At most 100 characters are allowed.");
  }

  @ParameterizedTest
  @ValueSource(strings = {"A\nB", "A\rB", "A\tB", "A\u0000B", "A B", "A\u0085B"})
  void rejectsControlCharactersInside(String value) {
    List<FieldViolation> errors = new ArrayList<>();

    assertThat(RegistrationValidator.text("lastName", value, 100, errors)).isNull();
    assertThat(errors)
        .containsExactly(new FieldViolation("lastName", RegistrationValidator.CONTROL));
  }

  @Test
  void acceptsUnicodeLetters() {
    assertThat(RegistrationValidator.hasControlCharacter("Čšž ĆĐ ü 李")).isFalse();
  }

  @ParameterizedTest
  @ValueSource(strings = {"a@b", "a b@c.si", "@c.si", "a@.", "a@@b.si"})
  void rejectsInvalidEmail(String email) {
    assertThat(fieldsOf(withEmail(external(), email))).containsExactly("email");
  }

  @Test
  void rejectsTooLongEmail() {
    String email = "a".repeat(250) + "@b.si";

    assertThat(fieldsOf(withEmail(external(), email))).containsExactly("email");
  }

  @Test
  void rejectsInactiveUnknownNullAndDuplicateOptions() {
    assertThat(fieldsOf(withOptions(external(), List.of("old")))).containsExactly("optionIds");
    assertThat(fieldsOf(withOptions(external(), List.of("nope")))).containsExactly("optionIds");
    List<String> withNull = new ArrayList<>();
    withNull.add(null);
    assertThat(fieldsOf(withOptions(external(), withNull))).containsExactly("optionIds");
    assertThat(fieldsOf(withOptions(external(), List.of("ws", "ws")))).containsExactly("optionIds");
  }

  @Test
  void rejectsTooManyOptions() {
    List<String> many = new ArrayList<>();
    for (int i = 0; i <= RegistrationValidator.OPTIONS_MAX; i++) {
      many.add("ws");
    }

    assertThat(fieldsOf(withOptions(external(), many))).containsExactly("optionIds");
  }

  @Test
  void acceptsExactlyTheMaximumNumberOfOptionEntriesWhenDistinctIsImpossible() {
    List<String> hundred = new ArrayList<>();
    for (int i = 0; i < RegistrationValidator.OPTIONS_MAX; i++) {
      hundred.add("ws");
    }

    assertThatThrownBy(() -> validator.validate(withOptions(external(), hundred)))
        .isInstanceOf(ValidationException.class)
        .extracting(e -> ((ValidationException) e).violations().get(0).message())
        .isEqualTo("Each option can be selected only once.");
  }

  @Test
  void rejectsMissingRequiredUnknownOrRepeatedConsents() {
    assertThat(fieldsOf(withConsents(external(), List.of("news")))).containsExactly("consents");
    assertThat(fieldsOf(withConsents(external(), List.of("privacy", "x"))))
        .containsExactly("consents");
    assertThat(fieldsOf(withConsents(external(), List.of("privacy", "privacy"))))
        .containsExactly("consents");
    List<String> withNull = new ArrayList<>();
    withNull.add(null);
    assertThat(fieldsOf(withConsents(external(), withNull))).containsExactly("consents");
  }

  @Test
  void rejectsBlankTooLongOrControlToken() {
    assertThat(fieldsOf(withToken(external(), " "))).containsExactly("recaptchaToken");
    assertThat(fieldsOf(withToken(external(), "x".repeat(4001)))).containsExactly("recaptchaToken");
    assertThat(fieldsOf(withToken(external(), "a\nb"))).containsExactly("recaptchaToken");
    assertThat(fieldsOf(withToken(external(), "x".repeat(4000)))).isEmpty();
  }

  private static RegistrationCommand withType(RegistrationCommand c, String type) {
    return new RegistrationCommand(
        type,
        c.firstName(),
        c.lastName(),
        c.email(),
        c.organization(),
        c.studyInstitution(),
        c.studyProgramme(),
        c.studentId(),
        c.optionIds(),
        c.consents(),
        c.recaptchaToken());
  }

  private static RegistrationCommand withEmail(RegistrationCommand c, String email) {
    return new RegistrationCommand(
        c.type(),
        c.firstName(),
        c.lastName(),
        email,
        c.organization(),
        c.studyInstitution(),
        c.studyProgramme(),
        c.studentId(),
        c.optionIds(),
        c.consents(),
        c.recaptchaToken());
  }

  private static RegistrationCommand withOptions(RegistrationCommand c, List<String> options) {
    return new RegistrationCommand(
        c.type(),
        c.firstName(),
        c.lastName(),
        c.email(),
        c.organization(),
        c.studyInstitution(),
        c.studyProgramme(),
        c.studentId(),
        options,
        c.consents(),
        c.recaptchaToken());
  }

  private static RegistrationCommand withConsents(RegistrationCommand c, List<String> consents) {
    return new RegistrationCommand(
        c.type(),
        c.firstName(),
        c.lastName(),
        c.email(),
        c.organization(),
        c.studyInstitution(),
        c.studyProgramme(),
        c.studentId(),
        c.optionIds(),
        consents,
        c.recaptchaToken());
  }

  private static RegistrationCommand withToken(RegistrationCommand c, String token) {
    return new RegistrationCommand(
        c.type(),
        c.firstName(),
        c.lastName(),
        c.email(),
        c.organization(),
        c.studyInstitution(),
        c.studyProgramme(),
        c.studentId(),
        c.optionIds(),
        c.consents(),
        token);
  }
}

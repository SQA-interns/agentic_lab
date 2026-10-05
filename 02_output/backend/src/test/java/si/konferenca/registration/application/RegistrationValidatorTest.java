package si.konferenca.registration.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import si.konferenca.registration.domain.OptionCategory;
import si.konferenca.registration.domain.RegistrationType;

class RegistrationValidatorTest {

  static final ConferenceOptions OPTIONS =
      new ConferenceOptions(
          new ConferenceOptions.Consent("c1", "Consent text"),
          List.of(
              new ConferenceOptions.Option("ws-a", "Workshop A", OptionCategory.WORKSHOP, true),
              new ConferenceOptions.Option("meal-b", "Meal B", OptionCategory.MEAL, true),
              new ConferenceOptions.Option("old", "Old", OptionCategory.EVENT, false)));

  private final RegistrationValidator validator = new RegistrationValidator();

  static RegistrationCommand external(String first, String email, String organization) {
    return new RegistrationCommand(
        "EXTERNAL",
        first,
        "Novak",
        email,
        organization,
        null,
        null,
        null,
        List.of("ws-a"),
        true,
        "token");
  }

  static RegistrationCommand student() {
    return new RegistrationCommand(
        "STUDENT",
        "Luka",
        "Kranjc",
        "luka@example.si",
        null,
        "UL",
        "FRI",
        "6321",
        List.of(),
        true,
        "token");
  }

  private List<FieldError> errors(RegistrationCommand command) {
    return catchThrowableOfType(
            RegistrationRejectedException.class, () -> validator.validate(command, OPTIONS))
        .errors();
  }

  @Test
  void acceptsValidExternalAndTrimsValues() {
    var valid = validator.validate(external("  Ana ", " ana@example.si ", " IJS "), OPTIONS);

    assertThat(valid.type()).isEqualTo(RegistrationType.EXTERNAL);
    assertThat(valid.participant().firstName()).isEqualTo("Ana");
    assertThat(valid.participant().email()).isEqualTo("ana@example.si");
    assertThat(valid.participant().organization()).isEqualTo("IJS");
    assertThat(valid.participant().studentId()).isNull();
    assertThat(valid.options()).extracting(ConferenceOptions.Option::id).containsExactly("ws-a");
    assertThat(valid.captchaToken()).isEqualTo("token");
  }

  @Test
  void acceptsValidStudentWithoutOptions() {
    var valid = validator.validate(student(), OPTIONS);

    assertThat(valid.type()).isEqualTo(RegistrationType.STUDENT);
    assertThat(valid.participant().studyInstitution()).isEqualTo("UL");
    assertThat(valid.participant().organization()).isNull();
    assertThat(valid.options()).isEmpty();
  }

  @Test
  void typeMissingOrUnknownIsRejected() {
    var missing =
        new RegistrationCommand(
            null, "A", "B", "a@b.si", null, null, null, null, List.of(), true, "t");
    var unknown =
        new RegistrationCommand(
            "VIP", "A", "B", "a@b.si", null, null, null, null, List.of(), true, "t");

    assertThat(errors(missing)).containsExactly(new FieldError("type", "REQUIRED"));
    assertThat(errors(unknown)).containsExactly(new FieldError("type", "NOT_ALLOWED"));
  }

  @Test
  void textAtTheLimitPassesAndOneMoreIsTooLong() {
    String hundred = "č".repeat(100);
    assertThat(
            validator.validate(external(hundred, "a@b.si", "x"), OPTIONS).participant().firstName())
        .hasSize(100);
    assertThat(errors(external(hundred + "a", "a@b.si", "x")))
        .containsExactly(new FieldError("firstName", "TOO_LONG"));
    assertThat(errors(external("A", "a@b.si", "o".repeat(201))))
        .containsExactly(new FieldError("organization", "TOO_LONG"));
  }

  @Test
  void lengthCountsCodePointsNotUtf16Units() {
    String emoji = "😀".repeat(100);
    assertThat(validator.validate(external(emoji, "a@b.si", "x"), OPTIONS)).isNotNull();
  }

  @Test
  void studentIdAndEmailLimits() {
    var longId =
        new RegistrationCommand(
            "STUDENT", "L", "K", "l@k.si", null, "UL", "FRI", "1".repeat(51), List.of(), true, "t");
    assertThat(errors(longId)).containsExactly(new FieldError("studentId", "TOO_LONG"));
    String email = "a".repeat(245) + "@example.si";
    assertThat(errors(external("A", email, "x")))
        .containsExactly(new FieldError("email", "TOO_LONG"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"An\u0000a", "An\na", "An a", "An a", "An\u0007a"})
  void controlCharactersAreRejected(String name) {
    assertThat(errors(external(name, "a@b.si", "x")))
        .containsExactly(new FieldError("firstName", "INVALID_CHARACTERS"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"a@b.si", "ana.novak+tag@sub.example.si", "š@žabe.si"})
  void validEmails(String email) {
    assertThat(validator.validate(external("A", email, "x"), OPTIONS).participant().email())
        .isEqualTo(email);
  }

  @ParameterizedTest
  @ValueSource(strings = {"a@b.s", "a<b@c.si", "a@b,c.si", "a;b@c.si", "\"a\"@b.si", "a@b\\c.si"})
  void invalidEmails(String email) {
    assertThat(errors(external("A", email, "x")))
        .containsExactly(new FieldError("email", "INVALID_EMAIL"));
  }

  @Test
  void fieldsOfTheOtherTypeAreNotAllowed() {
    var external =
        new RegistrationCommand(
            "EXTERNAL", "A", "B", "a@b.si", "IJS", "UL", "FRI", "1", List.of(), true, "t");
    var student =
        new RegistrationCommand(
            "STUDENT", "A", "B", "a@b.si", "", "UL", "FRI", "1", List.of(), true, "t");

    assertThat(errors(external))
        .containsExactly(
            new FieldError("studyInstitution", "NOT_ALLOWED"),
            new FieldError("studyProgramme", "NOT_ALLOWED"),
            new FieldError("studentId", "NOT_ALLOWED"));
    assertThat(errors(student)).containsExactly(new FieldError("organization", "NOT_ALLOWED"));
  }

  @Test
  void optionRules() {
    var missing =
        new RegistrationCommand(
            "STUDENT", "L", "K", "l@k.si", null, "UL", "FRI", "1", null, true, "t");
    List<String> tooMany = new ArrayList<>(Collections.nCopies(101, "ws-a"));
    var many =
        new RegistrationCommand(
            "STUDENT", "L", "K", "l@k.si", null, "UL", "FRI", "1", tooMany, true, "t");
    List<String> withNull = new ArrayList<>();
    withNull.add(null);
    var nullId =
        new RegistrationCommand(
            "STUDENT", "L", "K", "l@k.si", null, "UL", "FRI", "1", withNull, true, "t");
    var mixed =
        new RegistrationCommand(
            "STUDENT",
            "L",
            "K",
            "l@k.si",
            null,
            "UL",
            "FRI",
            "1",
            List.of("ws-a", "x", "old", "ws-a", "y"),
            true,
            "t");

    assertThat(errors(missing)).containsExactly(new FieldError("optionIds", "REQUIRED"));
    assertThat(errors(many)).containsExactly(new FieldError("optionIds", "TOO_LONG"));
    assertThat(errors(nullId)).containsExactly(new FieldError("optionIds", "UNKNOWN_OPTION"));
    assertThat(errors(mixed))
        .containsExactly(
            new FieldError("optionIds", "UNKNOWN_OPTION"),
            new FieldError("optionIds", "INACTIVE_OPTION"),
            new FieldError("optionIds", "DUPLICATE_OPTION"));
  }

  @Test
  void exactlyOneHundredOptionsAreAllowedByCount() {
    List<String> hundred = new ArrayList<>(Collections.nCopies(100, "ws-a"));
    var command =
        new RegistrationCommand(
            "STUDENT", "L", "K", "l@k.si", null, "UL", "FRI", "1", hundred, true, "t");
    assertThat(errors(command)).containsExactly(new FieldError("optionIds", "DUPLICATE_OPTION"));
  }

  @Test
  void consentAndCaptchaAreRequired() {
    var command =
        new RegistrationCommand(
            "STUDENT", "L", "K", "l@k.si", null, "UL", "FRI", "1", List.of(), false, "  ");
    var nullConsent =
        new RegistrationCommand(
            "STUDENT", "L", "K", "l@k.si", null, "UL", "FRI", "1", List.of(), null, null);

    assertThat(errors(command))
        .containsExactly(
            new FieldError("consentGiven", "CONSENT_REQUIRED"),
            new FieldError("captchaToken", "REQUIRED"));
    assertThat(errors(nullConsent)).hasSize(2);
  }

  @Test
  void allErrorsAreReportedTogether() {
    var command =
        new RegistrationCommand(
            "EXTERNAL", "", null, "bad", " ", null, null, null, List.of(), false, null);

    assertThatThrownBy(() -> validator.validate(command, OPTIONS))
        .isInstanceOf(RegistrationRejectedException.class);
    assertThat(errors(command)).hasSize(6);
  }
}

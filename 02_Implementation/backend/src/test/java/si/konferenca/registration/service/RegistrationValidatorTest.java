package si.konferenca.registration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import si.konferenca.registration.domain.ConferenceOption;
import si.konferenca.registration.domain.OptionCategory;
import si.konferenca.registration.domain.RegistrationType;
import si.konferenca.registration.service.FieldError.Code;
import si.konferenca.registration.service.RegistrationExceptions.ValidationFailedException;
import si.konferenca.registration.service.RegistrationValidator.ValidRegistration;

class RegistrationValidatorTest {

  private final RegistrationValidator validator = new RegistrationValidator();

  private final Map<String, ConferenceOption> catalog =
      Map.of(
          "ws-a", new ConferenceOption("ws-a", OptionCategory.WORKSHOP, "A", true, 0),
          "ws-b", new ConferenceOption("ws-b", OptionCategory.WORKSHOP, "B", true, 1),
          "ws-off", new ConferenceOption("ws-off", OptionCategory.WORKSHOP, "Off", false, 2));

  private static RegistrationCommand external(String email, List<String> optionIds) {
    return new RegistrationCommand(
        "EXTERNAL", "Ana", "Novak", email, "IJS", null, null, null, optionIds, true, "t");
  }

  private static RegistrationCommand student() {
    return new RegistrationCommand(
        "STUDENT", "Luka", "Šinkovec", "l@x.si", null, "FRI", "RI", "6320", List.of(), true, "t");
  }

  private List<FieldError> errorsOf(RegistrationCommand cmd) {
    try {
      validator.validate(cmd, catalog);
    } catch (ValidationFailedException e) {
      return e.errors();
    }
    return List.of();
  }

  @Test
  void acceptsValidExternalAndNormalizesWhitespace() {
    ValidRegistration valid =
        validator.validate(
            new RegistrationCommand(
                "EXTERNAL",
                "\u00a0Ana ",
                " Novak",
                " ana@x.si ",
                " IJS ",
                null,
                "",
                "  ",
                List.of("ws-a"),
                true,
                "t"),
            catalog);

    assertThat(valid.type()).isEqualTo(RegistrationType.EXTERNAL);
    assertThat(valid.participant().firstName()).isEqualTo("Ana");
    assertThat(valid.participant().email()).isEqualTo("ana@x.si");
    assertThat(valid.participant().studyProgramme()).isNull();
    assertThat(valid.options()).extracting(ConferenceOption::getId).containsExactly("ws-a");
  }

  @Test
  void acceptsValidStudentWithoutOrganization() {
    ValidRegistration valid = validator.validate(student(), catalog);

    assertThat(valid.type()).isEqualTo(RegistrationType.STUDENT);
    assertThat(valid.participant().organization()).isNull();
    assertThat(valid.participant().studentId()).isEqualTo("6320");
  }

  @Test
  void reportsAllFailingFieldsTogether() {
    List<FieldError> errors =
        errorsOf(
            new RegistrationCommand(
                "EXTERNAL", "", null, "bad", null, "FRI", null, null, null, false, "t"));

    assertThat(errors)
        .containsExactlyInAnyOrder(
            new FieldError("firstName", Code.REQUIRED),
            new FieldError("lastName", Code.REQUIRED),
            new FieldError("email", Code.INVALID_EMAIL),
            new FieldError("organization", Code.REQUIRED),
            new FieldError("studyInstitution", Code.FIELD_NOT_ALLOWED),
            new FieldError("personalDataConsent", Code.CONSENT_REQUIRED));
  }

  @Test
  void missingTypeIsRequiredAndUnknownTypeIsInvalid() {
    RegistrationCommand noType =
        new RegistrationCommand(null, "A", "B", "a@x.si", null, null, null, null, null, true, "t");
    RegistrationCommand badType =
        new RegistrationCommand("VIP", "A", "B", "a@x.si", null, null, null, null, null, true, "t");

    assertThat(errorsOf(noType)).containsExactly(new FieldError("type", Code.REQUIRED));
    assertThat(errorsOf(badType)).containsExactly(new FieldError("type", Code.INVALID_VALUE));
  }

  @Test
  void lengthLimitsCountCodePointsNotUtf16Units() {
    String hundredEmoji = "😀".repeat(100); // 200 UTF-16 units, 100 code points
    RegistrationCommand ok =
        new RegistrationCommand(
            "EXTERNAL", hundredEmoji, "B", "a@x.si", "IJS", null, null, null, null, true, "t");
    RegistrationCommand tooLong =
        new RegistrationCommand(
            "EXTERNAL",
            hundredEmoji + "x",
            "B",
            "a@x.si",
            "IJS",
            null,
            null,
            null,
            null,
            true,
            "t");

    assertThat(errorsOf(ok)).isEmpty();
    assertThat(errorsOf(tooLong)).containsExactly(new FieldError("firstName", Code.TOO_LONG));
  }

  @Test
  void studentIdLimitIsFifty() {
    RegistrationCommand cmd =
        new RegistrationCommand(
            "STUDENT", "A", "B", "a@x.si", null, "FRI", "RI", "1".repeat(51), null, true, "t");

    assertThat(errorsOf(cmd)).containsExactly(new FieldError("studentId", Code.TOO_LONG));
  }

  @ParameterizedTest
  @ValueSource(strings = {"Ana\u0000", "A\u200bna", "Ana\tNovak", "Ana\u0085x"})
  void controlAndFormatCharactersAreRejected(String value) {
    RegistrationCommand cmd =
        new RegistrationCommand(
            "EXTERNAL", value, "B", "a@x.si", "IJS", null, null, null, null, true, "t");

    assertThat(errorsOf(cmd)).containsExactly(new FieldError("firstName", Code.INVALID_CHARACTERS));
  }

  @Test
  void trailingUnicodeLineSeparatorIsStrippedNotRejected() {
    RegistrationCommand cmd =
        new RegistrationCommand(
            "EXTERNAL", "Ana\u2028", "B", "a@x.si", "IJS", null, null, null, null, true, "t");

    assertThat(validator.validate(cmd, catalog).participant().firstName()).isEqualTo("Ana");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"a@b", "a@.b.si", "a@b.si.", "a@b..si", "a b@c.si", "a@b@c.si", "@b.si", "a@"})
  void invalidEmailsAreRejected(String email) {
    assertThat(errorsOf(external(email, null)))
        .containsExactly(new FieldError("email", Code.INVALID_EMAIL));
  }

  @ParameterizedTest
  @ValueSource(strings = {"a@b.si", "ana.novak+konf@fri.uni-lj.si", "živa@primer.si"})
  void validEmailsAreAccepted(String email) {
    assertThat(errorsOf(external(email, null))).isEmpty();
  }

  @Test
  void consentMustBeExactlyTrue() {
    RegistrationCommand nullConsent =
        new RegistrationCommand(
            "EXTERNAL", "A", "B", "a@x.si", "IJS", null, null, null, null, null, "t");

    assertThat(errorsOf(nullConsent))
        .containsExactly(new FieldError("personalDataConsent", Code.CONSENT_REQUIRED));
  }

  @Test
  void duplicateOptionIdsAreCollapsed() {
    ValidRegistration valid =
        validator.validate(external("a@x.si", List.of("ws-a", "ws-b", "ws-a")), catalog);

    assertThat(valid.options()).extracting(ConferenceOption::getId).containsExactly("ws-a", "ws-b");
  }

  @Test
  void optionErrorsCarryTheirIndex() {
    List<String> ids = Arrays.asList("ws-a", "nope", "ws-off", null);

    assertThat(errorsOf(external("a@x.si", ids)))
        .containsExactly(
            new FieldError("optionIds[1]", Code.UNKNOWN_OPTION),
            new FieldError("optionIds[2]", Code.INACTIVE_OPTION),
            new FieldError("optionIds[3]", Code.UNKNOWN_OPTION));
  }

  @Test
  void moreThanFiftyOptionsAreRejected() {
    List<String> ids = new ArrayList<>(Collections.nCopies(51, "ws-a"));

    assertThat(errorsOf(external("a@x.si", ids)))
        .containsExactly(new FieldError("optionIds", Code.TOO_MANY_OPTIONS));
  }

  @Test
  void exactlyFiftyOptionsAreAllowed() {
    List<String> ids = new ArrayList<>(Collections.nCopies(50, "ws-a"));

    assertThat(errorsOf(external("a@x.si", ids))).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"studyInstitution", "studyProgramme", "studentId"})
  void eachStudentFieldIsNotAllowedOnAnExternalRegistration(String field) {
    RegistrationCommand cmd =
        new RegistrationCommand(
            "EXTERNAL",
            "A",
            "B",
            "a@x.si",
            "IJS",
            field.equals("studyInstitution") ? "x" : null,
            field.equals("studyProgramme") ? "x" : null,
            field.equals("studentId") ? "x" : null,
            null,
            true,
            "t");

    assertThat(errorsOf(cmd)).containsExactly(new FieldError(field, Code.FIELD_NOT_ALLOWED));
  }

  @Test
  void organizationIsNotAllowedOnAStudentRegistration() {
    RegistrationCommand cmd =
        new RegistrationCommand(
            "STUDENT", "A", "B", "a@x.si", "IJS", "FRI", "RI", "6320", null, true, "t");

    assertThat(errorsOf(cmd))
        .containsExactly(new FieldError("organization", Code.FIELD_NOT_ALLOWED));
  }

  @Test
  void validationExceptionListIsImmutable() {
    assertThatThrownBy(() -> validator.validate(external("bad", null), catalog))
        .isInstanceOfSatisfying(
            ValidationFailedException.class,
            e ->
                assertThatThrownBy(() -> e.errors().add(new FieldError("x", Code.REQUIRED)))
                    .isInstanceOf(UnsupportedOperationException.class));
  }
}

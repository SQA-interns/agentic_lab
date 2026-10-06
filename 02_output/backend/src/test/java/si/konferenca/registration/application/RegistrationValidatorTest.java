package si.konferenca.registration.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;
import si.konferenca.registration.domain.Registration;
import si.konferenca.registration.domain.RegistrationType;

class RegistrationValidatorTest {

  private final RegistrationValidator validator = new RegistrationValidator(Fixtures.CATALOG);
  private static final UUID ID = UUID.randomUUID();
  private static final Instant NOW = Instant.parse("2026-10-06T18:00:00Z");

  private static RegistrationCommand change(
      RegistrationCommand c, UnaryOperator<Object[]> modifier) {
    Object[] f = {
      c.type(),
      c.firstName(),
      c.lastName(),
      c.email(),
      c.organization(),
      c.studyInstitution(),
      c.studyProgramme(),
      c.studentId(),
      c.optionIds(),
      c.consentGiven(),
      c.recaptchaToken()
    };
    f = modifier.apply(f);
    @SuppressWarnings("unchecked")
    List<String> options = (List<String>) f[8];
    return new RegistrationCommand(
        (RegistrationType) f[0],
        (String) f[1],
        (String) f[2],
        (String) f[3],
        (String) f[4],
        (String) f[5],
        (String) f[6],
        (String) f[7],
        options,
        (Boolean) f[9],
        (String) f[10]);
  }

  private static RegistrationCommand with(RegistrationCommand c, int index, Object value) {
    return change(
        c,
        f -> {
          f[index] = value;
          return f;
        });
  }

  private List<FieldError> errors(RegistrationCommand c) {
    return catchThrowableOfType(ValidationException.class, () -> validator.validate(c, ID, NOW))
        .errors();
  }

  @Test
  void validExternalRegistrationIsNormalised() {
    RegistrationCommand c = with(Fixtures.external(), 1, "\u00A0 Ana ");

    Registration r = validator.validate(c, ID, NOW);

    assertThat(r.firstName()).isEqualTo("Ana");
    assertThat(r.id()).isEqualTo(ID);
    assertThat(r.receivedAt()).isEqualTo(NOW);
    assertThat(r.consent()).isEqualTo(Fixtures.CONSENT);
    assertThat(r.options()).containsExactly(Fixtures.WORKSHOP);
    assertThat(r.studentId()).isNull();
  }

  @Test
  void validStudentRegistrationKeepsStudentFields() {
    Registration r = validator.validate(Fixtures.student(), ID, NOW);

    assertThat(r.studyInstitution()).isEqualTo("UM");
    assertThat(r.studyProgramme()).isEqualTo("Informatika");
    assertThat(r.studentId()).isEqualTo("931");
    assertThat(r.organization()).isNull();
  }

  @Test
  void missingTypeIsRequiredAndAllTypeFieldsAreThenNotAllowed() {
    assertThat(errors(with(Fixtures.external(), 0, null)))
        .contains(new FieldError("type", "REQUIRED"))
        .contains(new FieldError("organization", "NOT_ALLOWED_FOR_TYPE"));
  }

  @Test
  void studentFieldsAreNotAllowedForExternal() {
    assertThat(errors(with(Fixtures.external(), 7, "123")))
        .containsExactly(new FieldError("studentId", "NOT_ALLOWED_FOR_TYPE"));
  }

  @Test
  void blankFieldOfOtherTypeIsIgnored() {
    Registration r = validator.validate(with(Fixtures.external(), 7, "\u00A0"), ID, NOW);

    assertThat(r.studentId()).isNull();
  }

  @Test
  void tooLongValuesAreRejected() {
    assertThat(errors(with(Fixtures.external(), 1, "a".repeat(101))))
        .containsExactly(new FieldError("firstName", "TOO_LONG"));
    assertThat(errors(with(Fixtures.student(), 7, "1".repeat(51))))
        .containsExactly(new FieldError("studentId", "TOO_LONG"));
    assertThat(errors(with(Fixtures.external(), 4, "o".repeat(201))))
        .containsExactly(new FieldError("organization", "TOO_LONG"));
  }

  @Test
  void maximumLengthsAreAccepted() {
    RegistrationCommand c = with(with(Fixtures.external(), 1, "a".repeat(100)), 4, "o".repeat(200));

    assertThat(validator.validate(c, ID, NOW).firstName()).hasSize(100);
  }

  @Test
  void longEmailIsTooLong() {
    String email = "a".repeat(250) + "@x.si";

    assertThat(errors(with(Fixtures.external(), 3, email)))
        .containsExactly(new FieldError("email", "TOO_LONG"));
  }

  @Test
  void controlCharactersAreRejectedAsInvalidCharacters() {
    assertThat(errors(with(Fixtures.external(), 2, "Novak\r\nBcc: x@y.si")))
        .containsExactly(new FieldError("lastName", "INVALID_CHARACTERS"));
  }

  @Test
  void emailWithInnerNoBreakSpaceIsInvalid() {
    assertThat(errors(with(Fixtures.external(), 3, "ana\u00A0b@example.si")))
        .containsExactly(new FieldError("email", "INVALID_EMAIL"));
  }

  @Test
  void optionErrorsCarryTheirIndex() {
    RegistrationCommand c =
        with(Fixtures.student(), 8, List.of("ws-ai", "missing", "meal-old", "ev-gala"));

    assertThat(errors(c))
        .containsExactly(
            new FieldError("optionIds[1]", "UNKNOWN_OPTION"),
            new FieldError("optionIds[2]", "INACTIVE_OPTION"),
            new FieldError("optionIds[3]", "OPTION_NOT_OFFERED"));
  }

  @Test
  void nullOptionIdIsUnknown() {
    assertThat(errors(with(Fixtures.external(), 8, Collections.singletonList(null))))
        .containsExactly(new FieldError("optionIds[0]", "UNKNOWN_OPTION"));
  }

  @Test
  void missingOptionListIsRequired() {
    assertThat(errors(with(Fixtures.external(), 8, null)))
        .containsExactly(new FieldError("optionIds", "REQUIRED"));
  }

  @Test
  void duplicateOptionIdsAreMalformed() {
    RegistrationCommand c = with(Fixtures.external(), 8, List.of("ws-ai", "ws-ai"));

    assertThatThrownBy(() -> validator.validate(c, ID, NOW))
        .isInstanceOf(MalformedRequestException.class);
  }

  @Test
  void tooManyOptionIdsAreMalformed() {
    String[] ids = new String[51];
    Arrays.setAll(ids, i -> "o" + i);
    RegistrationCommand c = with(Fixtures.external(), 8, List.of(ids));

    assertThatThrownBy(() -> validator.validate(c, ID, NOW))
        .isInstanceOf(MalformedRequestException.class);
  }

  @Test
  void fiftyOptionIdsAreStillValidatedOneByOne() {
    String[] ids = new String[50];
    Arrays.setAll(ids, i -> "o" + i);

    assertThat(errors(with(Fixtures.external(), 8, List.of(ids)))).hasSize(50);
  }

  @Test
  void consentMustBeTrueNotJustPresent() {
    assertThat(errors(with(Fixtures.external(), 9, null)))
        .containsExactly(new FieldError("consentGiven", "CONSENT_REQUIRED"));
    assertThat(errors(with(Fixtures.external(), 9, false)))
        .containsExactly(new FieldError("consentGiven", "CONSENT_REQUIRED"));
  }

  @Test
  void blankCaptchaTokenFailsWithOtherErrors() {
    RegistrationCommand c = with(with(Fixtures.external(), 10, " "), 1, "");

    assertThat(errors(c))
        .containsExactly(
            new FieldError("firstName", "REQUIRED"),
            new FieldError("recaptchaToken", "RECAPTCHA_FAILED"));
  }

  @Test
  void validationExceptionListsErrorCount() {
    ValidationException e = new ValidationException(List.of(new FieldError("a", "REQUIRED")));

    assertThat(e.getMessage()).contains("1 field error");
  }
}

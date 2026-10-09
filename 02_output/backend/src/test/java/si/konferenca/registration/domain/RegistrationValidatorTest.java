package si.konferenca.registration.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static si.konferenca.registration.domain.Fixtures.CLOCK;
import static si.konferenca.registration.domain.Fixtures.NOW;
import static si.konferenca.registration.domain.Fixtures.catalogue;
import static si.konferenca.registration.domain.Fixtures.external;
import static si.konferenca.registration.domain.Fixtures.externalValues;
import static si.konferenca.registration.domain.Fixtures.studentValues;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RegistrationValidatorTest {

  private final RegistrationValidator validator = new RegistrationValidator(catalogue(), CLOCK);

  private static List<String> codes(RegistrationValidator.Outcome outcome) {
    return outcome.errors().stream().map(e -> e.field() + ":" + e.code()).toList();
  }

  @Test
  void ac00102_validExternalSubmissionBuildsTheRegistration() {
    RegistrationValidator.Outcome outcome =
        validator.validate(external(externalValues(), List.of("ws-a", "ev-a")));

    assertThat(outcome.valid()).isTrue();
    Registration registration = outcome.registration();
    assertThat(registration.type()).isEqualTo(RegistrationType.EXTERNAL);
    assertThat(registration.submittedAt()).isEqualTo(NOW);
    assertThat(registration.selectedOptions())
        .extracting(ConferenceOption::id)
        .containsExactly("ws-a", "ev-a");
    assertThat(registration.consents()).extracting(GivenConsent::id).containsExactly("privacy");
    assertThat(registration.consents().getFirst().givenAt()).isEqualTo(NOW);
    assertThat(registration.consents().getFirst().text()).isEqualTo("I agree to processing.");
  }

  @Test
  void ac00202_validStudentSubmissionIsAccepted() {
    Submission submission =
        new Submission(RegistrationType.STUDENT, studentValues(), List.of(), List.of("privacy"));

    assertThat(validator.validate(submission).valid()).isTrue();
  }

  @Test
  void ac00106_valuesAreTrimmedIncludingNbsp() {
    Map<Field, String> values = externalValues();
    values.put(Field.FIRST_NAME, "  Ana  ");
    values.put(Field.EMAIL, " Ana@Example.si ");

    Registration registration = validator.validate(external(values, List.of())).registration();

    assertThat(registration.value(Field.FIRST_NAME)).isEqualTo("Ana");
    assertThat(registration.email()).isEqualTo("Ana@Example.si");
    assertThat(registration.normalizedEmail()).isEqualTo("ana@example.si");
  }

  @Test
  void ac00104_everyMissingFieldIsReportedTogether() {
    Map<Field, String> values = externalValues();
    values.remove(Field.FIRST_NAME);
    values.put(Field.ORGANIZATION, " ");

    assertThat(codes(validator.validate(external(values, List.of()))))
        .containsExactly("firstName:REQUIRED", "organization:REQUIRED");
  }

  @Test
  void fieldOfTheOtherTypeIsMalformed() {
    Map<Field, String> values = externalValues();
    values.put(Field.STUDENT_ID, "E1");

    assertThat(codes(validator.validate(external(values, List.of()))))
        .containsExactly("studentId:MALFORMED");
  }

  @Test
  void valueLongerThanTheLimitIsTooLong() {
    Map<Field, String> values = externalValues();
    values.put(Field.LAST_NAME, "x".repeat(101));
    values.put(Field.ORGANIZATION, "y".repeat(200));

    assertThat(codes(validator.validate(external(values, List.of()))))
        .containsExactly("lastName:TOO_LONG");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "ana",
        "ana@si",
        "a b@example.si",
        "ana@exa mple.si",
        "ana@@example.si",
        "ana x@example.si",
        "ana@example.si\nBcc:x@y.si"
      })
  void ac00107_invalidEmailsAreRejected(String email) {
    Map<Field, String> values = externalValues();
    values.put(Field.EMAIL, email);

    assertThat(codes(validator.validate(external(values, List.of()))))
        .containsExactly("email:INVALID_EMAIL");
  }

  @Test
  void controlCharacterInATextFieldIsMalformed() {
    Map<Field, String> values = externalValues();
    values.put(Field.ORGANIZATION, "IJS\u0007");

    assertThat(codes(validator.validate(external(values, List.of()))))
        .containsExactly("organization:MALFORMED");
  }

  @Test
  void ac00108_slovenianLettersAreKept() {
    Map<Field, String> values = externalValues();
    values.put(Field.LAST_NAME, "Šušteršič Čuček Žagar");

    assertThat(
            validator.validate(external(values, List.of())).registration().value(Field.LAST_NAME))
        .isEqualTo("Šušteršič Čuček Žagar");
  }

  @ParameterizedTest
  @ValueSource(strings = {"unknown", "ws-old"})
  void ac00110_ac00111_unknownOrInactiveOptionIsNotAvailable(String option) {
    assertThat(codes(validator.validate(external(externalValues(), List.of("ws-a", option)))))
        .containsExactly("optionIds:OPTION_NOT_AVAILABLE");
  }

  @Test
  void ac00206_optionForAnotherTypeIsNotAvailable() {
    Submission submission =
        new Submission(
            RegistrationType.STUDENT, studentValues(), List.of("ws-ext"), List.of("privacy"));

    assertThat(codes(validator.validate(submission)))
        .containsExactly("optionIds:OPTION_NOT_AVAILABLE");
  }

  @Test
  void ac00112_moreThanTheConfiguredMaximumIsRejected() {
    assertThat(
            codes(validator.validate(external(externalValues(), List.of("ws-a", "ws-b", "ws-c")))))
        .containsExactly("optionIds:TOO_MANY_OPTIONS");
  }

  @Test
  void d17_unconfiguredCategoryAllowsOne() {
    assertThat(codes(validator.validate(external(externalValues(), List.of("ev-a", "ev-b")))))
        .containsExactly("optionIds:TOO_MANY_OPTIONS");
    assertThat(validator.validate(external(externalValues(), List.of("ws-a", "ws-b"))).valid())
        .isTrue();
  }

  @Test
  void repeatedOptionIdCountsOnce() {
    RegistrationValidator.Outcome outcome =
        validator.validate(external(externalValues(), List.of("ev-a", "ev-a")));

    assertThat(outcome.valid()).isTrue();
    assertThat(outcome.registration().selectedOptions()).hasSize(1);
  }

  @Test
  void ac00114_missingMandatoryConsentNamesIt() {
    Submission submission =
        new Submission(RegistrationType.EXTERNAL, externalValues(), List.of(), List.of("photo"));

    RegistrationValidator.Outcome outcome = validator.validate(submission);

    assertThat(outcome.errors())
        .containsExactly(new ValidationError("consentIds", ErrorCode.CONSENT_REQUIRED, "privacy"));
  }

  @Test
  void optionalConsentIsStoredWhenGiven() {
    Submission submission =
        new Submission(
            RegistrationType.EXTERNAL, externalValues(), List.of(), List.of("photo", "privacy"));

    assertThat(validator.validate(submission).registration().consents())
        .extracting(GivenConsent::id)
        .containsExactly("privacy", "photo");
  }

  @Test
  void unknownConsentIsRejected() {
    Submission submission =
        new Submission(
            RegistrationType.EXTERNAL, externalValues(), List.of(), List.of("privacy", "spam"));

    assertThat(codes(validator.validate(submission))).containsExactly("consentIds:UNKNOWN_CONSENT");
  }
}

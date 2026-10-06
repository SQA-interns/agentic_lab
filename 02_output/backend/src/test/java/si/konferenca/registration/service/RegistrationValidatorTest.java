package si.konferenca.registration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import si.konferenca.registration.domain.Category;
import si.konferenca.registration.domain.ConferenceCatalog;
import si.konferenca.registration.domain.ConferenceOption;
import si.konferenca.registration.domain.ConsentDefinition;
import si.konferenca.registration.domain.RegistrationType;
import si.konferenca.registration.service.RegistrationRejectedException.FieldError;

class RegistrationValidatorTest {

  private static final Set<RegistrationType> BOTH = Set.of(RegistrationType.values());
  private static final ConferenceCatalog CATALOG =
      new ConferenceCatalog(
          List.of(
              new ConferenceOption("w1", "W1", Category.WORKSHOP, true, BOTH),
              new ConferenceOption("w2", "W2", Category.WORKSHOP, true, BOTH),
              new ConferenceOption(
                  "e1", "E1", Category.EVENT, true, Set.of(RegistrationType.EXTERNAL)),
              new ConferenceOption("m1", "M1", Category.MEAL, true, BOTH)),
          List.of(new ConsentDefinition("c1", "Consent 1"), new ConsentDefinition("c2", "C2")),
          Map.of(Category.WORKSHOP, 1));

  private final RegistrationValidator validator = new RegistrationValidator(CATALOG);

  private static RegistrationRequest external(
      String first, String email, String organization, List<String> options) {
    return new RegistrationRequest(
        "EXTERNAL",
        first,
        "Novak",
        email,
        organization,
        null,
        null,
        null,
        options,
        List.of("c1", "c2"),
        "t");
  }

  private static RegistrationRequest valid() {
    return external("Janez", "janez@example.com", "Org", List.of("w1", "m1"));
  }

  private List<FieldError> errors(RegistrationRequest request) {
    try {
      validator.validate(request);
      return List.of();
    } catch (RegistrationRejectedException e) {
      assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
      return e.fieldErrors();
    }
  }

  @Test
  void validRequestIsReturnedTrimmedWithOptionsAndConsents() {
    RegistrationValidator.Validated v =
        validator.validate(external(" Janez ", " janez@example.com ", " Org ", List.of("w1")));

    assertThat(v.type()).isEqualTo(RegistrationType.EXTERNAL);
    assertThat(v.participant().firstName()).isEqualTo("Janez");
    assertThat(v.participant().email()).isEqualTo("janez@example.com");
    assertThat(v.participant().organization()).isEqualTo("Org");
    assertThat(v.participant().studentId()).isNull();
    assertThat(v.options()).extracting(ConferenceOption::id).containsExactly("w1");
    assertThat(v.consents()).hasSize(2);
  }

  @Test
  void tooLongValuesAreRejected() {
    assertThat(errors(external("x".repeat(101), "a@b.si", "O", List.of())))
        .contains(new FieldError("firstName", FieldCode.TOO_LONG));
    assertThat(errors(external("J", "a@b.si", "o".repeat(201), List.of())))
        .contains(new FieldError("organization", FieldCode.TOO_LONG));
    assertThat(errors(external("x".repeat(100), "a@b.si", "o".repeat(200), List.of()))).isEmpty();
  }

  @Test
  void fieldsOfTheOtherTypeAreNotAllowed() {
    RegistrationRequest request =
        new RegistrationRequest(
            "EXTERNAL",
            "J",
            "N",
            "a@b.si",
            "Org",
            "Uni",
            null,
            "1",
            List.of(),
            List.of("c1", "c2"),
            "t");

    assertThat(errors(request))
        .contains(
            new FieldError("studyInstitution", FieldCode.NOT_ALLOWED),
            new FieldError("studentId", FieldCode.NOT_ALLOWED));
  }

  @Test
  void studentRequiresStudyFieldsAndRefusesOrganization() {
    RegistrationRequest request =
        new RegistrationRequest(
            "STUDENT",
            "J",
            "N",
            "a@b.si",
            "Org",
            null,
            " ",
            null,
            List.of(),
            List.of("c1", "c2"),
            "t");

    assertThat(errors(request))
        .contains(
            new FieldError("organization", FieldCode.NOT_ALLOWED),
            new FieldError("studyInstitution", FieldCode.REQUIRED),
            new FieldError("studyProgramme", FieldCode.REQUIRED),
            new FieldError("studentId", FieldCode.REQUIRED));
  }

  @Test
  void missingOrUnknownTypeIsReported() {
    RegistrationRequest missing =
        new RegistrationRequest(
            null, "J", "N", "a@b.si", null, null, null, null, List.of(), List.of("c1", "c2"), "t");
    RegistrationRequest unknown =
        new RegistrationRequest(
            "GUEST",
            "J",
            "N",
            "a@b.si",
            null,
            null,
            null,
            null,
            List.of(),
            List.of("c1", "c2"),
            "t");

    assertThat(errors(missing)).contains(new FieldError("type", FieldCode.REQUIRED));
    assertThat(errors(unknown)).contains(new FieldError("type", FieldCode.NOT_ALLOWED));
  }

  @Test
  void optionRulesAreChecked() {
    assertThat(errors(external("J", "a@b.si", "O", List.of("w1", "w1"))))
        .containsExactly(new FieldError("optionIds", FieldCode.DUPLICATE_OPTION));
    assertThat(errors(external("J", "a@b.si", "O", List.of("w1", "w2"))))
        .containsExactly(new FieldError("optionIds", FieldCode.CATEGORY_LIMIT));
    assertThat(errors(external("J", "a@b.si", "O", null)))
        .containsExactly(new FieldError("optionIds", FieldCode.REQUIRED));
    assertThat(errors(external("J", "a@b.si", "O", java.util.Collections.nCopies(51, "w1"))))
        .containsExactly(new FieldError("optionIds", FieldCode.TOO_LONG));
    assertThat(errors(external("J", "a@b.si", "O", java.util.Collections.nCopies(50, "zz"))))
        .containsExactly(
            new FieldError("optionIds", FieldCode.UNKNOWN_OPTION),
            new FieldError("optionIds", FieldCode.DUPLICATE_OPTION));
  }

  @Test
  void optionNotAvailableToStudentsIsRejectedButAvailableToExternals() {
    RegistrationRequest student =
        new RegistrationRequest(
            "STUDENT",
            "J",
            "N",
            "a@b.si",
            null,
            "U",
            "P",
            "1",
            List.of("e1"),
            List.of("c1", "c2"),
            "t");

    assertThat(errors(student))
        .containsExactly(new FieldError("optionIds", FieldCode.OPTION_NOT_AVAILABLE));
    assertThat(errors(external("J", "a@b.si", "O", List.of("e1")))).isEmpty();
  }

  @Test
  void everyConfiguredConsentIsRequiredAndUnknownOnesAreRefused() {
    RegistrationRequest oneMissing =
        new RegistrationRequest(
            "EXTERNAL", "J", "N", "a@b.si", "O", null, null, null, List.of(), List.of("c1"), "t");
    RegistrationRequest unknown =
        new RegistrationRequest(
            "EXTERNAL",
            "J",
            "N",
            "a@b.si",
            "O",
            null,
            null,
            null,
            List.of(),
            List.of("c1", "c2", "c9"),
            "t");
    RegistrationRequest absent =
        new RegistrationRequest(
            "EXTERNAL", "J", "N", "a@b.si", "O", null, null, null, List.of(), null, "t");

    assertThat(errors(oneMissing))
        .containsExactly(new FieldError("consentIds", FieldCode.CONSENT_MISSING));
    assertThat(errors(unknown))
        .containsExactly(new FieldError("consentIds", FieldCode.NOT_ALLOWED));
    assertThat(errors(absent))
        .containsExactly(new FieldError("consentIds", FieldCode.CONSENT_MISSING));
  }

  @Test
  void controlCharactersInsideAValueAreRejectedButSurroundingWhitespaceIsTrimmed() {
    assertThat(errors(external("Ja\u0000nez", "a@b.si", "O", List.of())))
        .containsExactly(new FieldError("firstName", FieldCode.CONTROL_CHARACTER));
    assertThat(errors(external("\tJanez\n", "a@b.si", "O", List.of()))).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"a@b.si", "ana.horvat@uni-lj.si", "x+tag@sub.example.com", "š@ž.si"})
  void acceptsEmails(String email) {
    assertThat(RegistrationValidator.validEmail(email)).isTrue();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"a@b", "a@.b.si", "a@b.si.", "a..b@c.si", "a@b..si", "@b.si", "a@", "a b@c.si"})
  void rejectsEmails(String email) {
    assertThat(RegistrationValidator.validEmail(email)).isFalse();
  }

  @Test
  void allErrorsAreCollectedInOneRejection() {
    assertThatThrownBy(() -> validator.validate(external("", "bad", "", List.of("zz"))))
        .isInstanceOfSatisfying(
            RegistrationRejectedException.class,
            e ->
                assertThat(e.fieldErrors())
                    .containsExactly(
                        new FieldError("firstName", FieldCode.REQUIRED),
                        new FieldError("organization", FieldCode.REQUIRED),
                        new FieldError("email", FieldCode.INVALID_FORMAT),
                        new FieldError("optionIds", FieldCode.UNKNOWN_OPTION)));
    assertThat(valid().optionIds()).containsExactly("w1", "m1");
  }
}

package si.konferenca.registration.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Rules of the validator that the acceptance tests do not reach (BR-02 to BR-05, SR-04). */
class RegistrationValidatorTest {

  private static final List<ConferenceOption> ACTIVE =
      IntStream.rangeClosed(1, 60)
          .mapToObj(
              i -> new ConferenceOption("opt-" + i, "Option " + i, OptionCategory.WORKSHOP, true))
          .toList();

  private final RegistrationValidator validator =
      new RegistrationValidator(
          new OptionsCatalogue() {
            @Override
            public List<ConferenceOption> activeOptions() {
              return ACTIVE;
            }

            @Override
            public Optional<ConferenceOption> findActive(String id) {
              return ACTIVE.stream().filter(option -> option.id().equals(id)).findFirst();
            }
          });

  private static Map<TextField, String> externalTexts() {
    Map<TextField, String> texts = new EnumMap<>(TextField.class);
    texts.put(TextField.FIRST_NAME, "Ana");
    texts.put(TextField.LAST_NAME, "Novak");
    texts.put(TextField.EMAIL, "ana.novak@example.org");
    texts.put(TextField.ORGANIZATION, "Podjetje");
    return texts;
  }

  private static RegistrationInput input(Map<TextField, String> texts, List<String> optionIds) {
    return new RegistrationInput(
        RegistrationType.EXTERNAL, texts, optionIds, true, "token", List.of(), Set.of());
  }

  private List<String> errorsOf(RegistrationInput input) {
    ValidationFailedException failure =
        catchThrowableOfType(ValidationFailedException.class, () -> validator.validate(input));
    assertThat(failure).as("the input should be rejected").isNotNull();
    return failure.errors().stream().map(error -> error.field() + ":" + error.code()).toList();
  }

  @Test
  void validInputIsReturnedTrimmedWithItsOptions() {
    Map<TextField, String> texts = externalTexts();
    texts.put(TextField.FIRST_NAME, "  Ana\t");

    RegistrationValidator.Validated validated = validator.validate(input(texts, List.of("opt-2")));

    // BR-02: a no-break space is whitespace too, like the tab.
    assertThat(validated.values().get(TextField.FIRST_NAME)).isEqualTo("Ana");
    assertThat(validated.options())
        .containsExactly(
            new Registration.SelectedOption("opt-2", "Option 2", OptionCategory.WORKSHOP));
  }

  @ParameterizedTest
  @ValueSource(strings = {" ", "   ", "　"})
  void br02_fieldOfUnicodeSpacesOnlyIsEmpty(String value) {
    Map<TextField, String> texts = externalTexts();
    texts.put(TextField.ORGANIZATION, value);

    assertThat(errorsOf(input(texts, List.of()))).containsExactly("organization:required");
  }

  @Test
  void textAtTheLimitIsAcceptedAndOneCharacterMoreIsTooLong() {
    Map<TextField, String> atLimit = externalTexts();
    atLimit.put(TextField.FIRST_NAME, "č".repeat(100));
    assertThat(validator.validate(input(atLimit, List.of())).values().get(TextField.FIRST_NAME))
        .hasSize(100);

    Map<TextField, String> overLimit = externalTexts();
    overLimit.put(TextField.FIRST_NAME, "č".repeat(101));
    overLimit.put(TextField.ORGANIZATION, "x".repeat(201));
    assertThat(errorsOf(input(overLimit, List.of())))
        .containsExactlyInAnyOrder("firstName:too_long", "organization:too_long");
  }

  @Test
  void lengthIsCountedInCharactersNotInUtf16Units() {
    Map<TextField, String> texts = externalTexts();
    // 100 characters outside the basic plane are 200 UTF-16 units.
    texts.put(TextField.FIRST_NAME, "😀".repeat(100));

    assertThat(validator.validate(input(texts, List.of())).values().get(TextField.FIRST_NAME))
        .hasSize(200);
  }

  @ParameterizedTest
  @ValueSource(strings = {"Ana\nNovak", "Ana\r\nBcc: x@example.org", "Ana\u0000", "Ana B", "A\tB"})
  void sr05_controlCharactersAndLineBreaksInsideATextAreRejected(String value) {
    Map<TextField, String> texts = externalTexts();
    texts.put(TextField.LAST_NAME, value);

    assertThat(errorsOf(input(texts, List.of()))).containsExactly("lastName:invalid_characters");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "ana@example.org.",
        "ana@.example.org",
        "ana@example..org",
        "ana@exa mple.org",
        "a@b@c.org"
      })
  void emailWithAMalformedDomainIsRejected(String email) {
    Map<TextField, String> texts = externalTexts();
    texts.put(TextField.EMAIL, email);

    assertThat(errorsOf(input(texts, List.of()))).containsExactly("email:invalid_format");
  }

  @ParameterizedTest
  @ValueSource(strings = {"a@b.si", "ime.priimek+oznaka@pod.domena.example.org", "čšž@example.org"})
  void usualEmailFormsAreAccepted(String email) {
    Map<TextField, String> texts = externalTexts();
    texts.put(TextField.EMAIL, email);

    assertThat(validator.validate(input(texts, List.of())).values().get(TextField.EMAIL))
        .isEqualTo(email);
  }

  @Test
  void emailLongerThan254CharactersIsTooLong() {
    Map<TextField, String> texts = externalTexts();
    texts.put(TextField.EMAIL, "a".repeat(244) + "@example.org");

    assertThat(errorsOf(input(texts, List.of()))).containsExactly("email:too_long");
  }

  @Test
  void fiftyOptionsAreAcceptedAndFiftyOneAreTooMany() {
    List<String> fifty = IntStream.rangeClosed(1, 50).mapToObj(i -> "opt-" + i).toList();
    assertThat(validator.validate(input(externalTexts(), fifty)).options()).hasSize(50);

    List<String> fiftyOne = IntStream.rangeClosed(1, 51).mapToObj(i -> "opt-" + i).toList();
    assertThat(errorsOf(input(externalTexts(), fiftyOne))).containsExactly("optionIds:too_many");
  }

  @Test
  void missingOptionListIsRequiredAndDuplicateAndUnknownAreBothReported() {
    assertThat(errorsOf(input(externalTexts(), null))).containsExactly("optionIds:required");

    List<String> ids = new ArrayList<>(List.of("opt-1", "opt-1", "nope"));
    assertThat(errorsOf(input(externalTexts(), ids)))
        .containsExactlyInAnyOrder("optionIds:option_duplicate", "optionIds:option_not_selectable");
  }

  @Test
  void everyBrokenRuleIsReportedTogether() {
    RegistrationInput input =
        new RegistrationInput(
            RegistrationType.STUDENT,
            Map.of(TextField.EMAIL, "not-an-email"),
            List.of("nope"),
            null,
            "  ",
            List.of(),
            Set.of());

    assertThat(errorsOf(input))
        .containsExactlyInAnyOrder(
            "firstName:required",
            "lastName:required",
            "email:invalid_format",
            "studyInstitution:required",
            "studyProgramme:required",
            "studentId:required",
            "optionIds:option_not_selectable",
            "consent:consent_required",
            "captchaToken:captcha_failed");
  }

  @Test
  void captchaTokenLongerThanTheLimitIsTooLong() {
    RegistrationInput input =
        new RegistrationInput(
            RegistrationType.EXTERNAL,
            externalTexts(),
            List.of(),
            true,
            "t".repeat(RegistrationValidator.MAX_CAPTCHA_TOKEN_LENGTH + 1),
            List.of(),
            Set.of());

    assertThat(errorsOf(input)).containsExactly("captchaToken:too_long");
  }

  @Test
  void fieldsAlreadyReportedAsMalformedAreNotReportedAgain() {
    Map<TextField, String> texts = externalTexts();
    texts.remove(TextField.FIRST_NAME);
    RegistrationInput input =
        new RegistrationInput(
            RegistrationType.EXTERNAL,
            texts,
            null,
            null,
            null,
            List.of(new FieldError("firstName", FieldError.INVALID_TYPE)),
            Set.of("firstName", "optionIds", "consent", "captchaToken"));

    assertThat(errorsOf(input)).containsExactly("firstName:invalid_type");
  }

  @Test
  void studentFieldOnAnExternalRegistrationIsIgnoredByTheRules() {
    Map<TextField, String> texts = externalTexts();
    texts.put(TextField.STUDENT_ID, "63210001");

    assertThat(validator.validate(input(texts, List.of())).values())
        .doesNotContainKey(TextField.STUDENT_ID);
  }
}

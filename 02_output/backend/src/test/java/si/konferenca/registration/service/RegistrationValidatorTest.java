package si.konferenca.registration.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import si.konferenca.registration.domain.Category;
import si.konferenca.registration.domain.ConferenceOption;
import si.konferenca.registration.domain.Consent;
import si.konferenca.registration.domain.FieldError;
import si.konferenca.registration.domain.OptionCatalogue;
import si.konferenca.registration.domain.RegistrationType;

class RegistrationValidatorTest {

  static final OptionCatalogue CATALOGUE =
      new OptionCatalogue(
          Map.of(Category.WORKSHOP, 1, Category.EVENT, 2),
          List.of(
              new ConferenceOption(
                  "ws-a", "A", Category.WORKSHOP, true, EnumSet.allOf(RegistrationType.class)),
              new ConferenceOption(
                  "ws-b", "B", Category.WORKSHOP, true, EnumSet.allOf(RegistrationType.class)),
              new ConferenceOption(
                  "ev-ext", "E", Category.EVENT, true, EnumSet.of(RegistrationType.EXTERNAL)),
              new ConferenceOption(
                  "ev-old", "O", Category.EVENT, false, EnumSet.allOf(RegistrationType.class))),
          List.of(new Consent("data", "Data", true), new Consent("news", "News", false)));

  static RegistrationCommand external() {
    return new RegistrationCommand(
        "EXTERNAL",
        "Ana",
        "Novak",
        "ana@example.si",
        "Org",
        null,
        null,
        null,
        List.of("ws-a"),
        List.of("data"),
        "t");
  }

  static RegistrationCommand student() {
    return new RegistrationCommand(
        "STUDENT",
        "Luka",
        "Horvat",
        "luka@example.si",
        null,
        "Uni",
        "Prog",
        "123",
        List.of(),
        List.of("data"),
        "t");
  }

  private static List<FieldError> errors(RegistrationCommand c) {
    return RegistrationValidator.validate(c, CATALOGUE).errors();
  }

  @Test
  void validExternalAndStudentHaveNoErrors() {
    assertThat(errors(external())).isEmpty();
    assertThat(errors(student())).isEmpty();
  }

  @Test
  @DisplayName("KP-03 values are trimmed of Unicode whitespace before they are kept")
  void valuesAreTrimmed() {
    RegistrationCommand c =
        new RegistrationCommand(
            "EXTERNAL",
            " Ana ",
            " Novak",
            "﻿ana@example.si ",
            " Org ",
            null,
            null,
            null,
            null,
            List.of("data"),
            "t");
    RegistrationValidator.Result r = RegistrationValidator.validate(c, CATALOGUE);
    assertThat(r.errors()).isEmpty();
    assertThat(r.participant().firstName()).isEqualTo("Ana");
    assertThat(r.participant().email()).isEqualTo("ana@example.si");
    assertThat(r.participant().organization()).isEqualTo("Org");
  }

  @Test
  void missingTypeIsRequiredAndUnknownTypeIsMalformed() {
    RegistrationCommand noType =
        new RegistrationCommand(
            null, "A", "B", "a@b.si", null, null, null, null, null, List.of("data"), "t");
    assertThat(errors(noType)).contains(new FieldError("type", "required"));
    RegistrationCommand badType =
        new RegistrationCommand(
            "VIP", "A", "B", "a@b.si", "Org", null, null, null, null, List.of("data"), "t");
    assertThat(errors(badType)).contains(new FieldError("type", "malformed"));
  }

  @Test
  @DisplayName("BR-01 fields of the other registration type are not allowed")
  void otherTypeFieldsAreRejected() {
    RegistrationCommand c =
        new RegistrationCommand(
            "EXTERNAL",
            "Ana",
            "Novak",
            "ana@example.si",
            "Org",
            "Uni",
            " ",
            null,
            null,
            List.of("data"),
            "t");
    assertThat(errors(c))
        .containsExactly(new FieldError("studyInstitution", "not_allowed_for_type"));
    RegistrationCommand s =
        new RegistrationCommand(
            "STUDENT",
            "Luka",
            "Horvat",
            "l@example.si",
            "Org",
            "Uni",
            "Prog",
            "1",
            null,
            List.of("data"),
            "t");
    assertThat(errors(s)).containsExactly(new FieldError("organization", "not_allowed_for_type"));
  }

  @Test
  void lengthsAreCountedAfterTrimmingInCodePoints() {
    RegistrationCommand ok =
        new RegistrationCommand(
            "STUDENT",
            "č".repeat(100),
            "B",
            "a@b.si",
            null,
            "U",
            "P",
            "1".repeat(50),
            null,
            List.of("data"),
            "t");
    assertThat(errors(ok)).isEmpty();
    RegistrationCommand tooLong =
        new RegistrationCommand(
            "STUDENT",
            "a".repeat(101),
            "B",
            "a@b.si",
            null,
            "U".repeat(201),
            "P",
            "1".repeat(51),
            null,
            List.of("data"),
            "t");
    assertThat(errors(tooLong))
        .containsExactlyInAnyOrder(
            new FieldError("firstName", "too_long"),
            new FieldError("studyInstitution", "too_long"),
            new FieldError("studentId", "too_long"));
  }

  @Test
  @DisplayName("SR-05 line breaks inside a value are rejected")
  void controlCharactersAreRejected() {
    RegistrationCommand c =
        new RegistrationCommand(
            "EXTERNAL",
            "Ana\r\nBcc: x@y.si",
            "Novak",
            "ana@example.si",
            "Org",
            null,
            null,
            null,
            null,
            List.of("data"),
            "t");
    assertThat(errors(c)).containsExactly(new FieldError("firstName", "invalid_characters"));
  }

  @Test
  void tooLongEmailIsNotAlsoReportedAsInvalid() {
    RegistrationCommand c =
        new RegistrationCommand(
            "EXTERNAL",
            "A",
            "B",
            "a".repeat(250) + "@x.si",
            "Org",
            null,
            null,
            null,
            null,
            List.of("data"),
            "t");
    assertThat(errors(c)).containsExactly(new FieldError("email", "too_long"));
  }

  @Test
  @DisplayName("BR-04 option errors: unknown, inactive, not available, too many")
  void optionRules() {
    assertThat(errors(withOptions(external(), "nope")))
        .containsExactly(new FieldError("optionIds", "unknown_option"));
    assertThat(errors(withOptions(external(), "ev-old")))
        .containsExactly(new FieldError("optionIds", "inactive_option"));
    assertThat(errors(withOptions(student(), "ev-ext")))
        .containsExactly(new FieldError("optionIds", "option_not_available"));
    assertThat(errors(withOptions(external(), "ws-a", "ws-b")))
        .containsExactly(new FieldError("optionIds", "too_many_options"));
    assertThat(errors(withOptions(external(), "ws-a", "ev-ext"))).isEmpty();
  }

  @Test
  void repeatedOptionIdCountsOnceAndNullIdIsUnknown() {
    assertThat(errors(withOptions(external(), "ws-a", "ws-a"))).isEmpty();
    RegistrationCommand c = withOptions(external(), (String) null);
    assertThat(errors(c)).containsExactly(new FieldError("optionIds", "unknown_option"));
  }

  @Test
  @DisplayName(
      "BR-05 mandatory consent missing, optional consent allowed, unknown consent rejected")
  void consentRules() {
    assertThat(errors(withConsents(external())))
        .containsExactly(new FieldError("consentIds", "consent_missing"));
    assertThat(errors(withConsents(external(), "news")))
        .containsExactly(new FieldError("consentIds", "consent_missing"));
    assertThat(errors(withConsents(external(), "data", "news"))).isEmpty();
    assertThat(errors(withConsents(external(), "data", "other")))
        .containsExactly(new FieldError("consentIds", "unknown_consent"));
  }

  @Test
  void allErrorsAreCollectedAtOnce() {
    RegistrationCommand c =
        new RegistrationCommand(
            "EXTERNAL", "", " ", "bad", null, null, null, null, List.of("nope"), List.of(), "t");
    assertThat(errors(c))
        .extracting(FieldError::field)
        .containsExactlyInAnyOrder(
            "firstName", "lastName", "email", "organization", "optionIds", "consentIds");
  }

  private static RegistrationCommand withOptions(RegistrationCommand c, String... ids) {
    return new RegistrationCommand(
        c.type(),
        c.firstName(),
        c.lastName(),
        c.email(),
        c.organization(),
        c.studyInstitution(),
        c.studyProgramme(),
        c.studentId(),
        java.util.Arrays.asList(ids),
        c.consentIds(),
        c.recaptchaToken());
  }

  private static RegistrationCommand withConsents(RegistrationCommand c, String... ids) {
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
        List.of(ids),
        c.recaptchaToken());
  }
}

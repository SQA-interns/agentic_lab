package si.konferenca.registration.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RegistrationRequestValidationTest {

  private static ValidatorFactory factory;
  private static Validator validator;

  @BeforeAll
  static void setUp() {
    factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
  }

  @AfterAll
  static void tearDown() {
    factory.close();
  }

  private static ExternalRegistrationRequest external(String firstName, String email) {
    return new ExternalRegistrationRequest(
        firstName, "Novak", email, "IJS", List.of(), Map.of("privacy", true), "token");
  }

  private static Set<String> codes(Set<? extends ConstraintViolation<?>> violations, String field) {
    return violations.stream()
        .filter(v -> v.getPropertyPath().toString().equals(field))
        .map(ConstraintViolation::getMessage)
        .collect(Collectors.toSet());
  }

  @Test
  void trimsAllTextFields() { // AC-001-09
    StudentRegistrationRequest request =
        new StudentRegistrationRequest(
            " Žiga ", "\tČeh\n", " z@example.si ", " UL ", " RI ", " 63 ", null, null, " token ");

    assertThat(request.firstName()).isEqualTo("Žiga");
    assertThat(request.lastName()).isEqualTo("Čeh");
    assertThat(request.email()).isEqualTo("z@example.si");
    assertThat(request.studyInstitution()).isEqualTo("UL");
    assertThat(request.studyProgramme()).isEqualTo("RI");
    assertThat(request.studentId()).isEqualTo("63");
    assertThat(request.captchaToken()).isEqualTo("token");
    assertThat(validator.validate(request)).isEmpty();
  }

  @Test
  void trimsUnicodeSpaceSeparators() {
    assertThat(ValidationCodes.trim("  Ana 　")).isEqualTo("Ana");
    assertThat(ValidationCodes.trim("   ")).isEmpty();
    assertThat(ValidationCodes.trim("Ana Marija")).isEqualTo("Ana Marija");
  }

  @Test
  void keepsNullValuesNull() {
    ExternalRegistrationRequest request =
        new ExternalRegistrationRequest(null, null, null, null, null, null, null);

    assertThat(request.firstName()).isNull();
    assertThat(codes(validator.validate(request), "firstName")).containsExactly("REQUIRED");
  }

  @Test
  void validRequestHasNoViolations() {
    assertThat(validator.validate(external("Ana", "ana@example.si"))).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"plain", "a@b", "a b@c.si", "@c.si", "a@.si "})
  void invalidEmailsAreRejected(String email) {
    assertThat(codes(validator.validate(external("Ana", email)), "email"))
        .contains("INVALID_EMAIL");
  }

  @ParameterizedTest
  @ValueSource(strings = {"ana.novak@example.si", "ž.č@uni-lj.si", "a+tag@sub.example.com"})
  void validEmailsAreAccepted(String email) {
    assertThat(codes(validator.validate(external("Ana", email)), "email")).isEmpty();
  }

  @Test
  void whitespaceOnlyIsRequiredViolation() {
    assertThat(codes(validator.validate(external("   ", "ana@example.si")), "firstName"))
        .contains("REQUIRED");
  }

  @Test
  void controlCharactersAreRejected() {
    assertThat(codes(validator.validate(external("A\u0007na", "ana@example.si")), "firstName"))
        .containsExactly("INVALID_CHARACTERS");
  }

  @Test
  void lengthBoundaries() {
    assertThat(validator.validate(external("a".repeat(100), "ana@example.si"))).isEmpty();
    assertThat(codes(validator.validate(external("a".repeat(101), "ana@example.si")), "firstName"))
        .containsExactly("TOO_LONG");
  }
}

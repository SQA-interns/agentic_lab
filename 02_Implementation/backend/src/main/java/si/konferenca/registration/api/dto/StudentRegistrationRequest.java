package si.konferenca.registration.api.dto;

import static si.konferenca.registration.api.dto.ValidationCodes.EMAIL_PATTERN;
import static si.konferenca.registration.api.dto.ValidationCodes.INVALID_CHARACTERS;
import static si.konferenca.registration.api.dto.ValidationCodes.INVALID_EMAIL;
import static si.konferenca.registration.api.dto.ValidationCodes.MAX_EMAIL;
import static si.konferenca.registration.api.dto.ValidationCodes.MAX_NAME;
import static si.konferenca.registration.api.dto.ValidationCodes.MAX_OPTIONS;
import static si.konferenca.registration.api.dto.ValidationCodes.MAX_STUDENT_ID;
import static si.konferenca.registration.api.dto.ValidationCodes.MAX_TEXT;
import static si.konferenca.registration.api.dto.ValidationCodes.MAX_TOKEN;
import static si.konferenca.registration.api.dto.ValidationCodes.NO_CONTROL_CHARS;
import static si.konferenca.registration.api.dto.ValidationCodes.REQUIRED;
import static si.konferenca.registration.api.dto.ValidationCodes.TOO_LONG;
import static si.konferenca.registration.api.dto.ValidationCodes.TOO_MANY_OPTIONS;
import static si.konferenca.registration.api.dto.ValidationCodes.trim;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

/** Request body of a student registration (FORM_SCHEMA — Student participant). */
public record StudentRegistrationRequest(
    @NotBlank(message = REQUIRED)
        @Size(max = MAX_NAME, message = TOO_LONG)
        @Pattern(regexp = NO_CONTROL_CHARS, message = INVALID_CHARACTERS)
        String firstName,
    @NotBlank(message = REQUIRED)
        @Size(max = MAX_NAME, message = TOO_LONG)
        @Pattern(regexp = NO_CONTROL_CHARS, message = INVALID_CHARACTERS)
        String lastName,
    @NotBlank(message = REQUIRED)
        @Size(max = MAX_EMAIL, message = TOO_LONG)
        @Email(message = INVALID_EMAIL)
        @Pattern(regexp = EMAIL_PATTERN, message = INVALID_EMAIL)
        String email,
    @NotBlank(message = REQUIRED)
        @Size(max = MAX_TEXT, message = TOO_LONG)
        @Pattern(regexp = NO_CONTROL_CHARS, message = INVALID_CHARACTERS)
        String studyInstitution,
    @NotBlank(message = REQUIRED)
        @Size(max = MAX_TEXT, message = TOO_LONG)
        @Pattern(regexp = NO_CONTROL_CHARS, message = INVALID_CHARACTERS)
        String studyProgramme,
    @NotBlank(message = REQUIRED)
        @Size(max = MAX_STUDENT_ID, message = TOO_LONG)
        @Pattern(regexp = NO_CONTROL_CHARS, message = INVALID_CHARACTERS)
        String studentId,
    @Size(max = MAX_OPTIONS, message = TOO_MANY_OPTIONS) List<String> optionIds,
    Map<String, Boolean> consents,
    @Size(max = MAX_TOKEN, message = TOO_LONG) String captchaToken) {

  public StudentRegistrationRequest {
    firstName = trim(firstName);
    lastName = trim(lastName);
    email = trim(email);
    studyInstitution = trim(studyInstitution);
    studyProgramme = trim(studyProgramme);
    studentId = trim(studentId);
    captchaToken = trim(captchaToken);
  }
}

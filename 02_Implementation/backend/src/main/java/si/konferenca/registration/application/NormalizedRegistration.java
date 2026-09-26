package si.konferenca.registration.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import si.konferenca.registration.domain.RegistrationType;

/**
 * Registration input after trimming. Constraints in the default group apply to every registration
 * type; {@link External} and {@link Student} hold the type-specific rules (FORM_SCHEMA).
 */
public record NormalizedRegistration(
    @NotNull(message = Messages.REQUIRED) RegistrationType type,
    @NotBlank(message = Messages.REQUIRED)
        @Size(max = 100, message = Messages.TOO_LONG)
        @Pattern(regexp = Messages.NO_CONTROL_CHARS, message = Messages.INVALID_CHARS)
        String firstName,
    @NotBlank(message = Messages.REQUIRED)
        @Size(max = 100, message = Messages.TOO_LONG)
        @Pattern(regexp = Messages.NO_CONTROL_CHARS, message = Messages.INVALID_CHARS)
        String lastName,
    @NotBlank(message = Messages.REQUIRED)
        @Size(max = 254, message = Messages.TOO_LONG)
        @Pattern(regexp = Messages.EMAIL_PATTERN, message = Messages.INVALID_EMAIL)
        String email,
    @NotBlank(groups = External.class, message = Messages.REQUIRED)
        @Null(groups = Student.class, message = Messages.NOT_APPLICABLE)
        @Size(max = 200, message = Messages.TOO_LONG)
        @Pattern(regexp = Messages.NO_CONTROL_CHARS, message = Messages.INVALID_CHARS)
        String organization,
    @NotBlank(groups = Student.class, message = Messages.REQUIRED)
        @Null(groups = External.class, message = Messages.NOT_APPLICABLE)
        @Size(max = 200, message = Messages.TOO_LONG)
        @Pattern(regexp = Messages.NO_CONTROL_CHARS, message = Messages.INVALID_CHARS)
        String studyInstitution,
    @NotBlank(groups = Student.class, message = Messages.REQUIRED)
        @Null(groups = External.class, message = Messages.NOT_APPLICABLE)
        @Size(max = 200, message = Messages.TOO_LONG)
        @Pattern(regexp = Messages.NO_CONTROL_CHARS, message = Messages.INVALID_CHARS)
        String studyProgramme,
    @NotBlank(groups = Student.class, message = Messages.REQUIRED)
        @Null(groups = External.class, message = Messages.NOT_APPLICABLE)
        @Size(max = 50, message = Messages.TOO_LONG)
        @Pattern(regexp = Messages.NO_CONTROL_CHARS, message = Messages.INVALID_CHARS)
        String studentId,
    List<String> optionIds,
    List<String> consentIds) {

  public NormalizedRegistration {
    optionIds = List.copyOf(optionIds);
    consentIds = List.copyOf(consentIds);
  }

  /** Validation group for external participant registrations. */
  public interface External {}

  /** Validation group for student registrations. */
  public interface Student {}

  /** User-facing validation messages. */
  public static final class Messages {
    public static final String REQUIRED = "This field is required.";
    public static final String TOO_LONG = "Must be at most {max} characters.";
    public static final String INVALID_CHARS = "Contains characters that are not allowed.";
    public static final String INVALID_EMAIL = "Enter a valid email address.";
    public static final String NOT_APPLICABLE = "Not applicable to this registration type.";
    public static final String UNKNOWN_OPTION = "Contains an unknown or unavailable option.";
    public static final String TOO_MANY_OPTIONS = "Too many options selected.";
    public static final String UNKNOWN_CONSENT = "Contains an unknown consent.";
    public static final String CONSENT_REQUIRED = "This consent is required.";

    static final String NO_CONTROL_CHARS = "\\P{Cc}*";
    static final String EMAIL_PATTERN =
        "^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?"
            + "(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+$";

    private Messages() {}
  }
}

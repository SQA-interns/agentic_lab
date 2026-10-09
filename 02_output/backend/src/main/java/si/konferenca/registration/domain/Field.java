package si.konferenca.registration.domain;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Fixed participant fields (BR-01) with their API names, labels and length limits. */
public enum Field {
  FIRST_NAME("firstName", "First name", 100, EnumSet.allOf(RegistrationType.class)),
  LAST_NAME("lastName", "Last name", 100, EnumSet.allOf(RegistrationType.class)),
  EMAIL("email", "Email", 254, EnumSet.allOf(RegistrationType.class)),
  ORGANIZATION(
      "organization", "Organization / institution", 200, EnumSet.of(RegistrationType.EXTERNAL)),
  STUDY_INSTITUTION(
      "studyInstitution", "Study institution", 200, EnumSet.of(RegistrationType.STUDENT)),
  STUDY_PROGRAMME("studyProgramme", "Study programme", 200, EnumSet.of(RegistrationType.STUDENT)),
  STUDENT_ID("studentId", "Student ID", 50, EnumSet.of(RegistrationType.STUDENT));

  private final String apiName;
  private final String label;
  private final int maxLength;
  private final Set<RegistrationType> types;

  Field(String apiName, String label, int maxLength, Set<RegistrationType> types) {
    this.apiName = apiName;
    this.label = label;
    this.maxLength = maxLength;
    this.types = types;
  }

  public String apiName() {
    return apiName;
  }

  public String label() {
    return label;
  }

  public int maxLength() {
    return maxLength;
  }

  public boolean belongsTo(RegistrationType type) {
    return types.contains(type);
  }

  /** The fields of a type, in display order. */
  public static List<Field> of(RegistrationType type) {
    return Arrays.stream(values()).filter(f -> f.belongsTo(type)).toList();
  }

  public static Optional<Field> fromApiName(String name) {
    return Arrays.stream(values()).filter(f -> f.apiName.equals(name)).findFirst();
  }
}

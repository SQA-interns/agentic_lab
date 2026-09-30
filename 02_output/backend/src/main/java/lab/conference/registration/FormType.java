package lab.conference.registration;

import java.util.List;

/** The two registration forms and their form-specific required fields (business-rules "Data"). */
public enum FormType {
  EXTERNAL("external", List.of("organization")),
  STUDENT("student", List.of("studyInstitution", "studyProgramme", "studentId"));

  private final String key;
  private final List<String> specificFields;

  FormType(String key, List<String> specificFields) {
    this.key = key;
    this.specificFields = specificFields;
  }

  public String key() {
    return key;
  }

  public List<String> specificFields() {
    return specificFields;
  }

  public static FormType fromKey(String key) {
    for (FormType t : values()) {
      if (t.key.equals(key)) {
        return t;
      }
    }
    throw new IllegalArgumentException("unknown form type");
  }
}

package si.konferenca.registration.domain;

/** The fixed participant fields (BR-01) with their name in the API and their maximum length. */
public enum TextField {
  FIRST_NAME("firstName", 100),
  LAST_NAME("lastName", 100),
  EMAIL("email", 254),
  ORGANIZATION("organization", 200),
  STUDY_INSTITUTION("studyInstitution", 200),
  STUDY_PROGRAMME("studyProgramme", 200),
  STUDENT_ID("studentId", 50);

  private final String apiName;
  private final int maxLength;

  TextField(String apiName, int maxLength) {
    this.apiName = apiName;
    this.maxLength = maxLength;
  }

  public String apiName() {
    return apiName;
  }

  public int maxLength() {
    return maxLength;
  }
}

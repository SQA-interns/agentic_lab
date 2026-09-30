package lab.conference.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.UUID;
import lab.conference.TestCatalogs;
import lab.conference.options.GroupId;
import lab.conference.platform.ApiException;
import lab.conference.platform.FieldError;
import org.junit.jupiter.api.Test;

class RegistrationValidatorTest {

  private static final ObjectMapper JSON = new ObjectMapper();
  private final RegistrationValidator validator =
      new RegistrationValidator(TestCatalogs.withConsent(true));

  private static ObjectNode external() {
    ObjectNode n = JSON.createObjectNode();
    n.put("clientRequestId", UUID.randomUUID().toString());
    n.put("captchaToken", "tok");
    n.put("firstName", " Ana ");
    n.put("lastName", "Kovač");
    n.put("email", "ana@example.test");
    n.put("organization", "Org");
    ObjectNode sel = n.putObject("selections");
    sel.putArray("workshops").add("ws-a");
    n.put("consentGiven", true);
    return n;
  }

  private List<FieldError> errors(FormType form, JsonNode body) {
    try {
      validator.validate(form, body);
    } catch (ApiException e) {
      assertThat(e.status().value()).isEqualTo(400);
      return e.errors();
    }
    throw new AssertionError("expected validation failure");
  }

  private static boolean has(List<FieldError> errors, String field, String code) {
    return errors.stream().anyMatch(e -> e.field().equals(field) && e.code().equals(code));
  }

  @Test
  void validExternalIsNormalised() {
    ValidatedRegistration r = validator.validate(FormType.EXTERNAL, external());
    assertThat(r.field("firstName")).isEqualTo("Ana");
    assertThat(r.fields().keySet())
        .containsExactly("firstName", "lastName", "email", "organization");
    assertThat(r.selections().get(GroupId.WORKSHOPS)).extracting("id").containsExactly("ws-a");
    assertThat(r.selections().get(GroupId.MEALS)).isEmpty();
    assertThat(r.consent()).isEqualTo(new ValidatedRegistration.ConsentState("c1", true));
    assertThat(r.captchaToken()).isEqualTo("tok");
  }

  @Test
  void fingerprintIsStableAndContentSensitive() {
    ObjectNode body = external();
    String a = validator.validate(FormType.EXTERNAL, body).fingerprint();
    String b = validator.validate(FormType.EXTERNAL, body.deepCopy()).fingerprint();
    body.put("firstName", "Ana ");
    assertThat(validator.validate(FormType.EXTERNAL, body).fingerprint()).isEqualTo(a);
    body.put("lastName", "Other");
    assertThat(validator.validate(FormType.EXTERNAL, body).fingerprint()).isNotEqualTo(a);
    assertThat(a).isEqualTo(b).hasSize(64);
    ObjectNode captchaChanged = external();
    captchaChanged.put("clientRequestId", body.get("clientRequestId").asText());
    captchaChanged.put("lastName", "Other");
    captchaChanged.put("captchaToken", "different-token");
    assertThat(validator.validate(FormType.EXTERNAL, captchaChanged).fingerprint())
        .as("captcha token is not content")
        .isEqualTo(validator.validate(FormType.EXTERNAL, body).fingerprint());
  }

  @Test
  void fingerprintDiffersByFormTypeSelectionsAndConsent() {
    RegistrationValidator noConsent = new RegistrationValidator(TestCatalogs.withConsent(false));
    ObjectNode body = external();
    String base = noConsent.validate(FormType.EXTERNAL, body).fingerprint();
    body.put("consentGiven", false);
    assertThat(noConsent.validate(FormType.EXTERNAL, body).fingerprint()).isNotEqualTo(base);
    ObjectNode sel = external();
    ((ObjectNode) sel.get("selections")).putArray("events").add("ev-a");
    assertThat(validator.validate(FormType.EXTERNAL, sel).fingerprint())
        .isNotEqualTo(validator.validate(FormType.EXTERNAL, external()).fingerprint());
  }

  @Test
  void allErrorsAreCollectedTogether() {
    ObjectNode body = JSON.createObjectNode();
    body.put("clientRequestId", "nope");
    body.put("email", "bad");
    body.put("consentGiven", false);
    body.put("extra", 1);
    List<FieldError> errors = errors(FormType.STUDENT, body);
    assertThat(has(errors, "clientRequestId", "INVALID_FORMAT")).isTrue();
    assertThat(has(errors, "firstName", "REQUIRED")).isTrue();
    assertThat(has(errors, "email", "INVALID_EMAIL")).isTrue();
    assertThat(has(errors, "studentId", "REQUIRED")).isTrue();
    assertThat(has(errors, "captchaToken", "REQUIRED")).isTrue();
    assertThat(has(errors, "consentGiven", "CONSENT_REQUIRED")).isTrue();
    assertThat(has(errors, "extra", "UNKNOWN_FIELD")).isTrue();
  }

  @Test
  void missingClientRequestIdIsRequired() {
    ObjectNode body = external();
    body.remove("clientRequestId");
    assertThat(has(errors(FormType.EXTERNAL, body), "clientRequestId", "REQUIRED")).isTrue();
    body.put("clientRequestId", 12);
    assertThat(has(errors(FormType.EXTERNAL, body), "clientRequestId", "INVALID_FORMAT")).isTrue();
  }

  @Test
  void studentFieldOnExternalFormIsUnknown() {
    ObjectNode body = external();
    body.put("studentId", "1");
    assertThat(has(errors(FormType.EXTERNAL, body), "studentId", "UNKNOWN_FIELD")).isTrue();
  }

  @Test
  void nonTextValuesAreInvalidFormat() {
    ObjectNode body = external();
    body.put("firstName", 5);
    assertThat(has(errors(FormType.EXTERNAL, body), "firstName", "INVALID_FORMAT")).isTrue();
  }

  @Test
  void lengthIsCountedInCodePoints() {
    ObjectNode body = external();
    body.put("firstName", "😀".repeat(100));
    assertThat(validator.validate(FormType.EXTERNAL, body).field("firstName")).hasSize(200);
    body.put("firstName", "😀".repeat(101));
    assertThat(has(errors(FormType.EXTERNAL, body), "firstName", "TOO_LONG")).isTrue();
  }

  @Test
  void controlCharactersAreRejected() {
    ObjectNode body = external();
    body.put("organization", "Org\nBcc: x@y.test");
    assertThat(has(errors(FormType.EXTERNAL, body), "organization", "INVALID_CHARACTERS")).isTrue();
  }

  @Test
  void selectionProblemsAreReportedPerGroup() {
    ObjectNode body = external();
    ObjectNode sel = body.putObject("selections");
    sel.putArray("workshops").add("ws-off").add("ws-a").add("ws-a");
    sel.putArray("events").add(3);
    sel.putArray("meals").add("ev-a");
    sel.put("other", "x");
    sel.putArray("tours");
    List<FieldError> errors = errors(FormType.EXTERNAL, body);
    assertThat(has(errors, "selections.workshops", "UNKNOWN_OPTION")).isTrue();
    assertThat(has(errors, "selections.workshops", "DUPLICATE_OPTION")).isTrue();
    assertThat(has(errors, "selections.events", "INVALID_FORMAT")).isTrue();
    assertThat(has(errors, "selections.meals", "UNKNOWN_OPTION")).isTrue();
    assertThat(has(errors, "selections.other", "INVALID_FORMAT")).isTrue();
    assertThat(has(errors, "selections.tours", "UNKNOWN_FIELD")).isTrue();
  }

  @Test
  void tooManyOptionsAndNonObjectSelections() {
    ObjectNode body = external();
    var arr = body.putObject("selections").putArray("workshops");
    for (int i = 0; i < 51; i++) {
      arr.add("ws-" + i);
    }
    assertThat(has(errors(FormType.EXTERNAL, body), "selections.workshops", "TOO_MANY_OPTIONS"))
        .isTrue();
    body.put("selections", "x");
    assertThat(has(errors(FormType.EXTERNAL, body), "selections", "INVALID_FORMAT")).isTrue();
  }

  @Test
  void nullOrMissingSelectionsMeanNone() {
    ObjectNode body = external();
    body.remove("selections");
    assertThat(validator.validate(FormType.EXTERNAL, body).selections().values())
        .allMatch(List::isEmpty);
    body.putNull("selections");
    assertThat(validator.validate(FormType.EXTERNAL, body).selections()).hasSize(4);
    body.putObject("selections").putNull("meals");
    assertThat(validator.validate(FormType.EXTERNAL, body).selections().get(GroupId.MEALS))
        .isEmpty();
  }

  @Test
  void consentMustBeBooleanAndTrueWhenRequired() {
    ObjectNode body = external();
    body.put("consentGiven", "yes");
    assertThat(has(errors(FormType.EXTERNAL, body), "consentGiven", "INVALID_FORMAT")).isTrue();
    body.remove("consentGiven");
    assertThat(has(errors(FormType.EXTERNAL, body), "consentGiven", "CONSENT_REQUIRED")).isTrue();
  }

  @Test
  void optionalConsentIsStoredEitherWay() {
    RegistrationValidator optional = new RegistrationValidator(TestCatalogs.withConsent(false));
    ObjectNode body = external();
    body.put("consentGiven", false);
    assertThat(optional.validate(FormType.EXTERNAL, body).consent())
        .isEqualTo(new ValidatedRegistration.ConsentState("c1", false));
  }

  @Test
  void noConsentFixtureMeansNullConsent() {
    RegistrationValidator none = new RegistrationValidator(TestCatalogs.withoutConsent());
    assertThat(none.validate(FormType.EXTERNAL, external()).consent()).isNull();
  }

  @Test
  void nonObjectBodyIsMalformed() {
    assertThatThrownBy(() -> validator.validate(FormType.EXTERNAL, JSON.createArrayNode()))
        .isInstanceOf(ApiException.class)
        .extracting(e -> ((ApiException) e).errors().get(0).code())
        .isEqualTo("MALFORMED_REQUEST");
    assertThatThrownBy(() -> validator.validate(FormType.EXTERNAL, null))
        .isInstanceOf(ApiException.class);
  }

  @Test
  void studentFormRequiresStudyFields() {
    ObjectNode body = external();
    body.remove("organization");
    body.put("studyInstitution", "U");
    body.put("studyProgramme", "P");
    body.put("studentId", "S1");
    ValidatedRegistration r = validator.validate(FormType.STUDENT, body);
    assertThat(r.fields().keySet())
        .containsExactly(
            "firstName", "lastName", "email", "studyInstitution", "studyProgramme", "studentId");
  }
}

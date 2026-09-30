package lab.conference.registration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lab.conference.notifications.NotificationKind;
import lab.conference.notifications.NotificationRequest;
import lab.conference.options.CatalogOption;
import lab.conference.options.GroupId;
import org.junit.jupiter.api.Test;

class RecordAndMailTest {

  private static ValidatedRegistration registration(ValidatedRegistration.ConsentState consent) {
    Map<String, String> fields = new LinkedHashMap<>();
    fields.put("firstName", "Žiga");
    fields.put("lastName", "<b>Š</b>");
    fields.put("email", "z@example.test");
    fields.put("studyInstitution", "U");
    fields.put("studyProgramme", "P");
    fields.put("studentId", "S1");
    Map<GroupId, List<CatalogOption>> selections = new java.util.EnumMap<>(GroupId.class);
    selections.put(GroupId.WORKSHOPS, List.of(new CatalogOption("w1", "Workshop – A", true)));
    selections.put(GroupId.EVENTS, List.of());
    selections.put(
        GroupId.MEALS,
        List.of(new CatalogOption("m1", "Lunch", true), new CatalogOption("m2", "Dinner", true)));
    selections.put(GroupId.OTHER, List.of());
    return new ValidatedRegistration(
        UUID.randomUUID(), FormType.STUDENT, fields, selections, consent, "tok");
  }

  @Test
  void recordFollowsTheContractShape() throws Exception {
    UUID id = UUID.randomUUID();
    ValidatedRegistration r = registration(new ValidatedRegistration.ConsentState("c1", true));
    JsonNode json =
        new ObjectMapper()
            .readTree(RegistrationRecord.toJson(id, Instant.parse("2026-09-30T10:00:00.123Z"), r));
    assertThat(json.fieldNames())
        .toIterable()
        .containsExactly(
            "schemaVersion",
            "registrationId",
            "clientRequestId",
            "formType",
            "acceptedAt",
            "participant",
            "selections",
            "consent");
    assertThat(json.path("acceptedAt").asText()).isEqualTo("2026-09-30T10:00:00.123Z");
    assertThat(json.path("formType").asText()).isEqualTo("student");
    assertThat(json.path("participant").path("studentId").asText()).isEqualTo("S1");
    assertThat(json.path("selections").path("meals").findValuesAsText("name"))
        .containsExactly("Lunch", "Dinner");
    assertThat(json.path("selections").path("other").isArray()).isTrue();
    assertThat(json.path("consent").path("given").asBoolean()).isTrue();
    assertThat(json.has("captchaToken")).isFalse();
  }

  @Test
  void recordWithoutConsentFixtureHasNullConsent() throws Exception {
    JsonNode json =
        new ObjectMapper()
            .readTree(
                RegistrationRecord.toJson(UUID.randomUUID(), Instant.now(), registration(null)));
    assertThat(json.get("consent").isNull()).isTrue();
  }

  @Test
  void mailsArePlainTextWithFixedSubjects() {
    UUID id = UUID.randomUUID();
    ValidatedRegistration r = registration(new ValidatedRegistration.ConsentState("c1", false));
    List<NotificationRequest> mails =
        MailComposer.compose(id, r, "a".repeat(64), List.of("o1@example.test", "o2@example.test"));
    assertThat(mails).hasSize(3);
    NotificationRequest participant = mails.get(0);
    assertThat(participant.kind()).isEqualTo(NotificationKind.PARTICIPANT);
    assertThat(participant.recipient()).isEqualTo("z@example.test");
    assertThat(participant.subject()).isEqualTo(MailComposer.PARTICIPANT_SUBJECT);
    assertThat(participant.attachmentName()).isNull();
    assertThat(participant.body())
        .contains(
            id.toString(), "Žiga", "<b>Š</b>", "Workshops: Workshop – A", "Meals: Lunch; Dinner");
    NotificationRequest organizer = mails.get(1);
    assertThat(organizer.kind()).isEqualTo(NotificationKind.ORGANIZER);
    assertThat(organizer.subject()).isEqualTo("New registration " + id);
    assertThat(organizer.attachmentName()).isEqualTo("registration-" + id + ".json");
    assertThat(organizer.attachmentSha256()).hasSize(64);
    assertThat(organizer.body())
        .contains("Student ID: S1", "Consent (c1): no", r.clientRequestId().toString());
    assertThat(mails.get(2).recipient()).isEqualTo("o2@example.test");
  }

  @Test
  void mailWithoutSelectionsSaysNone() {
    ValidatedRegistration r = registration(null);
    Map<GroupId, List<CatalogOption>> empty = new java.util.EnumMap<>(GroupId.class);
    ValidatedRegistration none =
        new ValidatedRegistration(
            r.clientRequestId(), FormType.EXTERNAL, r.fields(), empty, null, "t");
    String body =
        MailComposer.compose(UUID.randomUUID(), none, "x", List.of("o@example.test")).get(1).body();
    assertThat(body).contains("- none").doesNotContain("Consent");
  }

  @Test
  void organizerRecipientsAreValidated() {
    assertThat(new OrganizerRecipients(List.of(" a@example.test ", "")).addresses())
        .containsExactly("a@example.test");
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> new OrganizerRecipients(List.of()))
        .isInstanceOf(IllegalStateException.class);
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> new OrganizerRecipients(null))
        .isInstanceOf(IllegalStateException.class);
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> new OrganizerRecipients(List.of("not an address")))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void formTypesResolveByKey() {
    assertThat(FormType.fromKey("student")).isEqualTo(FormType.STUDENT);
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> FormType.fromKey("x"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}

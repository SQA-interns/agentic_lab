package si.konferenca.registration.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import si.konferenca.registration.domain.OptionCategory;
import si.konferenca.registration.domain.Participant;
import si.konferenca.registration.domain.Registration;
import si.konferenca.registration.domain.RegistrationType;

class RegistrationEmailsTest {

  private static final UUID ID = UUID.fromString("3f1c2a9e-5b7d-4c1e-9a2b-8d6e4f0a1b2c");
  private static final Instant AT = Instant.parse("2026-10-06T08:00:00.123Z");
  private final RegistrationEmails emails =
      new RegistrationEmails("Konferenca 2026", List.of("a@org.si", "b@org.si"));

  private static Registration student(
      List<Registration.SelectedOption> options, List<Registration.GivenConsent> consents) {
    return new Registration(
        ID,
        RegistrationType.STUDENT,
        new Participant("Žiga", "Čebašek", "ziga@example.si", null, "UL", "Računalništvo", "6321"),
        options,
        consents,
        AT);
  }

  @Test
  void participantEmailWithoutOptionsSaysSo() {
    RegistrationEmails.EmailMessage message =
        emails.participantConfirmation(student(List.of(), List.of()));

    assertThat(message.to()).containsExactly("ziga@example.si");
    assertThat(message.subject()).isEqualTo("Registration confirmed: Konferenca 2026");
    assertThat(message.text())
        .contains("Dear Žiga Čebašek,")
        .contains("Registration ID: " + ID)
        .contains("Registration type: Student")
        .contains("No options selected");
    assertThat(message.attachment()).isNull();
  }

  @Test
  void participantEmailGroupsOptionsByCategoryInCategoryOrder() {
    String text =
        emails
            .participantConfirmation(
                student(
                    List.of(
                        new Registration.SelectedOption("m", "Kosilo", OptionCategory.MEAL),
                        new Registration.SelectedOption("w1", "WS 1", OptionCategory.WORKSHOP),
                        new Registration.SelectedOption("w2", "WS 2", OptionCategory.WORKSHOP)),
                    List.of()))
            .text();

    assertThat(text).contains("Workshops: WS 1, WS 2\n").contains("Meals: Kosilo\n");
    assertThat(text.indexOf("Workshops")).isLessThan(text.indexOf("Meals"));
    assertThat(text).doesNotContain("Events:").doesNotContain("No options selected");
  }

  @Test
  void organizerEmailListsStudentFieldsConsentsAndAttachesTheCopy() {
    byte[] copy = "{\"a\":1}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    RegistrationEmails.EmailMessage message =
        emails.organizerNotification(
            student(List.of(), List.of(new Registration.GivenConsent("data", "I agree", AT))),
            copy);

    assertThat(message.to()).containsExactly("a@org.si", "b@org.si");
    assertThat(message.subject()).isEqualTo("New registration: Konferenca 2026");
    assertThat(message.text())
        .contains("Received at (UTC): 2026-10-06T08:00:00.123Z")
        .contains("Study institution: UL")
        .contains("Study programme: Računalništvo")
        .contains("Student ID: 6321")
        .doesNotContain("Organization / institution")
        .contains("- data (2026-10-06T08:00:00.123Z): I agree");
    assertThat(message.attachment().filename()).isEqualTo("registration-" + ID + ".json");
    assertThat(message.attachment().contentType()).isEqualTo("application/json");
    assertThat(message.attachment().content()).isEqualTo(copy);
  }

  @Test
  void organizerEmailForExternalShowsOrganizationAndNoConsentsLine() {
    Registration external =
        new Registration(
            ID,
            RegistrationType.EXTERNAL,
            new Participant("Ana", "Novak", "ana@example.si", "IJS", null, null, null),
            List.of(),
            List.of(),
            AT);

    String text = emails.organizerNotification(external, new byte[0]).text();

    assertThat(text)
        .contains("Organization / institution: IJS")
        .doesNotContain("Student ID")
        .contains("- none");
  }

  @Test
  void organizerEmailHasEveryFieldLineAndTheOptions() {
    Registration external =
        new Registration(
            ID,
            RegistrationType.EXTERNAL,
            new Participant("Ana", "Novak", "ana@example.si", "IJS", null, null, null),
            List.of(new Registration.SelectedOption("t", "Ogled", OptionCategory.OTHER)),
            List.of(),
            AT);

    String text = emails.organizerNotification(external, new byte[0]).text();

    assertThat(text)
        .contains("Registration ID: " + ID + "\n")
        .contains("Registration type: External participant\n")
        .contains("First name: Ana\n")
        .contains("Last name: Novak\n")
        .contains("Email: ana@example.si\n")
        .contains("Selected options:\nOther activities: Ogled\n");
  }

  @Test
  void attachmentContentCannotBeChangedFromOutside() {
    byte[] copy = {1, 2};
    RegistrationEmails.Attachment attachment = new RegistrationEmails.Attachment("f", "t", copy);
    copy[0] = 9;
    attachment.content()[1] = 9;

    assertThat(attachment.content()).containsExactly(1, 2);
  }
}

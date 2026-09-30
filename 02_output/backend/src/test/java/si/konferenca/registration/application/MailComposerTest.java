package si.konferenca.registration.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import si.konferenca.registration.domain.ParticipantDetails;
import si.konferenca.registration.domain.Registration;
import si.konferenca.registration.domain.RegistrationType;

class MailComposerTest {

  @Test
  void participantMailListsOptionsByCategoryAndKeepsUserInputOutOfTheSubject() {
    Registration r = NotificationServiceTest.registration(RegistrationType.EXTERNAL);

    OutgoingMail mail = MailComposer.participantMail(r, "Konferenca 2026");

    assertThat(mail.subject()).isEqualTo("Registration confirmed – Konferenca 2026");
    assertThat(mail.subject()).doesNotContain("Ana");
    assertThat(mail.to()).containsExactly("a@example.si");
    assertThat(mail.attachment()).isNull();
    assertThat(mail.text())
        .contains("Dear Ana Novak,")
        .contains("Registration reference: " + r.reference())
        .contains("Registration type: External participant")
        .contains("- Workshops: Workshop A")
        .contains("- Meals: Lunch")
        .doesNotContain("Events");
  }

  @Test
  void organizerMailHasAllStudentFieldsConsentsAndTheAttachment() {
    Registration r = NotificationServiceTest.registration(RegistrationType.STUDENT);
    byte[] json = "{}".getBytes();

    OutgoingMail mail = MailComposer.organizerMail(r, "K", List.of("o@x.si"), json);

    assertThat(mail.subject()).isEqualTo("New registration – K");
    assertThat(mail.text())
        .contains("Reference: " + r.reference() + "\n")
        .contains("Submitted at (UTC): " + r.submittedAt() + "\n")
        .contains("First name: Luka\n")
        .contains("Last name: Kovač\n")
        .contains("Email: l@example.si\n")
        .contains("- Workshops: Workshop A\n");
    assertThat(mail.text())
        .contains("Type: Student")
        .contains("Study institution: UL")
        .contains("Study programme: RI")
        .contains("Student ID: 63")
        .contains("- dp (")
        .doesNotContain("Organization")
        .doesNotContain("PENDING");
    assertThat(mail.attachment()).isEqualTo(json);
    json[0] = 'x';
    assertThat(mail.attachment()[0]).as("defensive copy").isEqualTo((byte) '{');
  }

  @Test
  void noOptionsIsStated() {
    Registration r =
        Registration.accept(
            UUID.randomUUID(),
            new ParticipantDetails(
                RegistrationType.EXTERNAL, "A", "B", "a@b.si", "O", null, null, null),
            List.of(),
            List.of(),
            NotificationServiceTest.NOW);

    assertThat(MailComposer.participantMail(r, "K").text()).contains("- none");
    assertThat(MailComposer.organizerMail(r, "K", List.of("o@x.si"), new byte[0]).text())
        .contains("Organization / institution: O");
  }
}

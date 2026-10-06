package si.konferenca.registration.infrastructure.mail;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import si.konferenca.registration.application.Fixtures;
import si.konferenca.registration.domain.Registration;
import si.konferenca.registration.domain.RegistrationType;

class EmailTextsTest {

  @Test
  void subjectsFollowTheContractPatterns() {
    Registration r = Fixtures.registration();

    assertThat(EmailTexts.participantSubject("Konf")).matches("^Registration received: .+$");
    assertThat(EmailTexts.organizerSubject(r, "Konf")).isEqualTo("New external registration: Konf");
  }

  @Test
  void participantBodyGroupsOptionsByCategory() {
    String body = EmailTexts.participantBody(Fixtures.registration(), "Konf");

    assertThat(body)
        .contains("Dear Ana Novak")
        .contains("Workshops: AI workshop")
        .contains("Events: Gala dinner")
        .contains("Registration type: External participant")
        .doesNotContain("Meals:");
  }

  @Test
  void bodiesSayWhenNoOptionWasSelected() {
    Registration r = Fixtures.registration();
    Registration none =
        new Registration(
            r.id(),
            RegistrationType.STUDENT,
            "Luka",
            "K",
            "l@x.si",
            null,
            "UM",
            "Inf",
            "1",
            List.of(),
            r.consent(),
            r.receivedAt());

    assertThat(EmailTexts.participantBody(none, "K")).contains("No options selected.");
    assertThat(EmailTexts.organizerBody(none))
        .contains("Student ID: 1")
        .contains("Study institution: UM")
        .doesNotContain("Organization / institution:");
  }

  @Test
  void organizerBodyListsEveryFieldAndConsent() {
    assertThat(EmailTexts.organizerBody(Fixtures.registration()))
        .contains("Registration ID: 0b9f7a52-5c1e-4c55-9d1e-3f1f1f6a2b10")
        .contains("Received at (UTC): 2026-10-06T18:00:00.123Z")
        .contains("Type: External participant")
        .contains("First name: Ana")
        .contains("Last name: Novak")
        .contains("Email: Ana@Example.si")
        .contains("Organization / institution: IJS")
        .contains("Consent given: I agree.")
        .doesNotContain("Student ID");
  }
}

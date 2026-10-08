package si.konferenca.registration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import si.konferenca.registration.config.StartupChecksTest;
import si.konferenca.registration.domain.Category;
import si.konferenca.registration.domain.GivenConsent;
import si.konferenca.registration.domain.Participant;
import si.konferenca.registration.domain.Registration;
import si.konferenca.registration.domain.RegistrationType;
import si.konferenca.registration.domain.SelectedOption;
import si.konferenca.registration.infrastructure.JsonCopyStore;
import si.konferenca.registration.infrastructure.MailGateway;

public class NotificationServiceTest {

  private final MailGateway mail = mock(MailGateway.class);
  private final JsonCopyStore copies = mock(JsonCopyStore.class);
  private final NotificationService service =
      new NotificationService(mail, copies, StartupChecksTest.validProduction());

  public static Registration student() {
    Instant at = Instant.parse("2026-10-08T10:00:00Z");
    return new Registration(
        UUID.fromString("3f2b8c1e-7a4d-4e8b-9c61-2d5f0a9e4b17"),
        at,
        RegistrationType.STUDENT,
        new Participant("Žiga", "Kovač", "ziga@example.si", null, "Uni LJ", "RI", "6321"),
        List.of(
            new SelectedOption("ws", "Delavnica", Category.WORKSHOP),
            new SelectedOption("m1", "Lunch", Category.MEAL),
            new SelectedOption("m2", "Dinner", Category.MEAL)),
        List.of(new GivenConsent("data", "I agree", at)));
  }

  @Test
  @DisplayName("US-006 participant email: fixed subject, plain text with fields and options")
  void participantMail() {
    MailGateway.Mail m = service.participantMail(student());

    assertThat(m.to()).containsExactly("ziga@example.si");
    assertThat(m.subject()).isEqualTo("Registration confirmed: Conf");
    assertThat(m.attachment()).isNull();
    assertThat(m.body())
        .contains("Registration type: Student\n")
        .contains("First name: Žiga\n")
        .contains("Study institution: Uni LJ\n")
        .contains("Student ID: 6321\n")
        .contains("Workshops: Delavnica\n")
        .contains("Meals: Lunch; Dinner\n")
        .doesNotContain("Organization")
        .doesNotContain("Events:");
  }

  @Test
  @DisplayName("US-007 organizer email: all recipients, id, consents and the stored copy attached")
  void organizerMail() throws Exception {
    when(copies.read(any())).thenReturn("{\"id\":1}".getBytes());

    MailGateway.Mail m = service.organizerMail(student());

    assertThat(m.to()).containsExactly("a@example.si", "b@example.si");
    assertThat(m.subject()).isEqualTo("New registration (STUDENT): Conf");
    assertThat(m.body())
        .startsWith(
            "Registration id: 3f2b8c1e-7a4d-4e8b-9c61-2d5f0a9e4b17\nReceived at: 2026-10-08T10:00:00Z\n")
        .contains("Consents: data (2026-10-08T10:00:00Z)");
    assertThat(m.attachment().fileName())
        .isEqualTo("registration-3f2b8c1e-7a4d-4e8b-9c61-2d5f0a9e4b17.json");
    assertThat(m.attachment().content()).isEqualTo("{\"id\":1}".getBytes());
  }

  @Test
  @DisplayName("D-13 a failed participant email does not stop the organizer email")
  void failuresAreIsolatedAndSwallowed() throws Exception {
    when(copies.read(any())).thenReturn(new byte[] {1});
    doThrow(new IllegalStateException("smtp down")).doNothing().when(mail).send(any());

    service.registrationAccepted(student());

    ArgumentCaptor<MailGateway.Mail> sent = ArgumentCaptor.forClass(MailGateway.Mail.class);
    verify(mail, times(2)).send(sent.capture());
    assertThat(sent.getAllValues().get(1).subject()).startsWith("New registration");
  }

  @Test
  void unreadableCopyIsLoggedNotThrown() throws Exception {
    when(copies.read(any())).thenThrow(new IOException("gone"));

    service.registrationAccepted(student());

    verify(mail, times(1)).send(any());
  }
}

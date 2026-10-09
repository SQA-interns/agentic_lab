package si.konferenca.registration.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.stream.Stream;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;
import si.konferenca.registration.application.CaptchaVerifier;
import si.konferenca.registration.application.RegistrationCopyStore;
import si.konferenca.registration.domain.GivenConsent;
import si.konferenca.registration.domain.ParticipantDetails;
import si.konferenca.registration.domain.Registration;
import si.konferenca.registration.domain.RegistrationType;

/** Adapter details that the first mutation run showed untested. */
class AdapterDetailsTest {

  @TempDir Path dir;

  private static final Instant AT = Instant.parse("2026-10-09T08:15:30.123Z");

  private static Registration registration(RegistrationType type, ParticipantDetails participant) {
    return new Registration(
        UUID.fromString("11111111-2222-3333-4444-555555555555"),
        type,
        participant,
        AT,
        List.of(),
        List.of(new GivenConsent("data", "I agree.", AT)));
  }

  private static Registration external() {
    return registration(
        RegistrationType.EXTERNAL,
        new ParticipantDetails("Ana", "Novak", "ana@example.si", "IJS", null, null, null));
  }

  private static Registration student() {
    return registration(
        RegistrationType.STUDENT,
        new ParticipantDetails("Luka", "Kranjc", "luka@example.si", null, "FRI", "RI", "6320"));
  }

  private static String organizerText(Registration registration) throws Exception {
    JavaMailSender sender = mock(JavaMailSender.class);
    when(sender.createMimeMessage())
        .thenAnswer(invocation -> new MimeMessage(Session.getInstance(new Properties())));
    new SmtpRegistrationNotifier(sender, "f@example.si", "Conf", List.of("o@example.org"))
        .notifyOrganizers(registration, new byte[] {'{', '}'});
    ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
    verify(sender).send(captor.capture());
    MimeMessage message = captor.getValue();
    message.saveChanges();
    Object first = ((Multipart) message.getContent()).getBodyPart(0).getContent();
    // With an attachment the text sits inside a nested "related" multipart.
    return first instanceof Multipart nested
        ? (String) nested.getBodyPart(0).getContent()
        : (String) first;
  }

  @Test
  void organizerEmailListsEveryExternalField() throws Exception {
    assertThat(organizerText(external()))
        .contains("Registration ID: 11111111-2222-3333-4444-555555555555\n")
        .contains("Registration type: External participant\n")
        .contains("First name: Ana\n")
        .contains("Last name: Novak\n")
        .contains("Email: ana@example.si\n")
        .contains("Organization: IJS\n")
        .doesNotContain("Study institution")
        .doesNotContain("Study programme");
  }

  @Test
  void organizerEmailListsEveryStudentField() throws Exception {
    assertThat(organizerText(student()))
        .contains("Registration type: Student\n")
        .contains("Study institution: FRI\n")
        .contains("Study programme: RI\n")
        .contains("Student ID: 6320\n")
        .doesNotContain("Organization:");
  }

  @Test
  void externalCopyHasTheOrganizationAndNoStudentFields() {
    byte[] json = new FileRegistrationCopyStore(dir).write(external());

    assertThat(new String(json, StandardCharsets.UTF_8))
        .contains(
            "\"participant\":{\"firstName\":\"Ana\",\"lastName\":\"Novak\","
                + "\"email\":\"ana@example.si\",\"organization\":\"IJS\"}");
  }

  @Test
  void failedMoveRemovesTheTemporaryFile() throws Exception {
    FileRegistrationCopyStore store = new FileRegistrationCopyStore(dir);
    Path target = dir.resolve("registration-11111111-2222-3333-4444-555555555555.json");
    Files.createDirectory(target);
    Files.writeString(target.resolve("occupied"), "x");

    assertThatThrownBy(() -> store.write(external()))
        .isInstanceOf(RegistrationCopyStore.CopyStoreException.class);

    try (Stream<Path> files = Files.list(dir)) {
      assertThat(files.map(p -> p.getFileName().toString()))
          .containsExactly("registration-11111111-2222-3333-4444-555555555555.json");
    }
  }

  @Test
  void headerRowIsBold() throws Exception {
    byte[] xlsx = new PoiWorkbookWriter().write("S", List.of("A"), List.of(List.of("v")));

    try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
      var header = workbook.getSheet("S").getRow(0).getCell(0).getCellStyle();
      var data = workbook.getSheet("S").getRow(1).getCell(0).getCellStyle();
      assertThat(workbook.getFontAt(header.getFontIndex()).getBold()).isTrue();
      assertThat(workbook.getFontAt(data.getFontIndex()).getBold()).isFalse();
    }
  }

  @Test
  void interruptedVerificationIsUnavailableAndKeepsTheInterrupt() {
    RecaptchaCaptchaVerifier verifier =
        new RecaptchaCaptchaVerifier(URI.create("http://127.0.0.1:9/siteverify"), "s");
    Thread.currentThread().interrupt();
    try {
      assertThatThrownBy(() -> verifier.verify("t", "ip"))
          .isInstanceOf(CaptchaVerifier.CaptchaUnavailableException.class);
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
    } finally {
      Thread.interrupted();
    }
  }
}

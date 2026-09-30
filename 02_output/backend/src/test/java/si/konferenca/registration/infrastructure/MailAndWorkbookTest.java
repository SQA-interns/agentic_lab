package si.konferenca.registration.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.mail.Address;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import si.konferenca.registration.application.RegistrationCopy;

class MailAndWorkbookTest {

  private static RegistrationCopy student() {
    Instant at = Instant.parse("2026-09-30T10:00:00Z");
    return new RegistrationCopy(
        1,
        UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"),
        at,
        "STUDENT",
        "<b>Luka</b>",
        "Kovač",
        "luka@example.si",
        null,
        "UL",
        "=SUM(A1)",
        "63",
        List.of(
            new RegistrationCopy.Option("ws", "WS", "workshop"),
            new RegistrationCopy.Option("ev", "EV", "event"),
            new RegistrationCopy.Option("m1", "M1", "meal"),
            new RegistrationCopy.Option("m2", "M2", "meal"),
            new RegistrationCopy.Option("ot", "OT", "other")),
        List.of(
            new RegistrationCopy.Consent("privacy", at), new RegistrationCopy.Consent("news", at)));
  }

  @Test
  void participantTextListsNameTypeAndOptions() {
    String text = MailTexts.participantBody(student(), "Konferenca");

    assertThat(text)
        .startsWith("Dear <b>Luka</b> Kovač,\n\n")
        .contains("your registration for Konferenca has been received.")
        .contains("Registration ID: aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee\n")
        .contains("Registration type: Student\n")
        .contains(
            "- WS (Workshop)\n- EV (Event)\n- M1 (Meal)\n- M2 (Meal)\n- OT (Other activity)\n")
        .endsWith("This is an automatic message.\n");
    assertThat(MailTexts.participantSubject("K")).isEqualTo("Registration confirmed: K");
    assertThat(MailTexts.typeLabel("EXTERNAL")).isEqualTo("External participant");
  }

  @Test
  void textWithoutOptionsSaysNone() {
    RegistrationCopy r = student();
    RegistrationCopy none =
        new RegistrationCopy(
            1,
            r.id(),
            r.submittedAt(),
            "EXTERNAL",
            "A",
            "B",
            "a@b.si",
            "Org",
            null,
            null,
            null,
            List.of(),
            List.of());

    assertThat(MailTexts.participantBody(none, "K")).contains("Selected options:\n- none\n");
    assertThat(MailTexts.organizerBody(none))
        .contains("Organization / institution: Org\n")
        .doesNotContain("Student ID");
  }

  @Test
  void organizerTextHasOneLabelledLinePerField() {
    String text = MailTexts.organizerBody(student());

    assertThat(text)
        .startsWith(
            "Registration ID: aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee\n"
                + "Submitted at (UTC): 2026-09-30T10:00:00Z\n"
                + "Type: STUDENT\n"
                + "First name: <b>Luka</b>\n"
                + "Last name: Kovač\n"
                + "Email: luka@example.si\n"
                + "Study institution: UL\n"
                + "Study programme: =SUM(A1)\n"
                + "Student ID: 63\n")
        .contains("Consents:\n- privacy (2026-09-30T10:00:00Z)\n- news (2026-09-30T10:00:00Z)\n")
        .doesNotContain("Organization");
    assertThat(MailTexts.organizerSubject(student()))
        .isEqualTo("New registration (STUDENT): aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
  }

  private static JavaMailSender sender() {
    JavaMailSender sender = mock(JavaMailSender.class);
    when(sender.createMimeMessage())
        .thenAnswer(i -> new MimeMessage(Session.getInstance(new Properties())));
    return sender;
  }

  private static MimeMessage sent(JavaMailSender sender) {
    org.mockito.ArgumentCaptor<MimeMessage> captor =
        org.mockito.ArgumentCaptor.forClass(MimeMessage.class);
    verify(sender).send(captor.capture());
    return captor.getValue();
  }

  @Test
  void participantEmailIsPlainUtf8Text() throws Exception {
    JavaMailSender sender = sender();

    new SmtpNotificationSender(sender, TestProperties.with("x", "y"))
        .sendParticipantConfirmation(student());

    MimeMessage m = sent(sender);
    m.saveChanges();
    assertThat(m.getContentType()).startsWith("text/plain").containsIgnoringCase("utf-8");
    assertThat(Arrays.stream(m.getAllRecipients()).map(Address::toString))
        .containsExactly("luka@example.si");
    assertThat(m.getFrom()[0].toString()).isEqualTo("from@conference.test");
    assertThat(m.getSubject()).isEqualTo("Registration confirmed: Konferenca");
    assertThat((String) m.getContent()).contains("<b>Luka</b>");
  }

  @Test
  void organizerEmailGoesToAllOrganizersWithTheExactJsonAttached() throws Exception {
    JavaMailSender sender = sender();
    byte[] json = "{\"a\":\"č\"}".getBytes(StandardCharsets.UTF_8);

    new SmtpNotificationSender(sender, TestProperties.with("x", "y"))
        .sendOrganizerNotification(student(), json);

    MimeMessage m = sent(sender);
    m.saveChanges();
    assertThat(Arrays.stream(m.getAllRecipients()).map(Address::toString))
        .containsExactly("a@org.test", "b@org.test");
    assertThat(m.getContentType()).startsWith("multipart/mixed");
    Multipart root = (Multipart) m.getContent();
    List<String> fileNames = new ArrayList<>();
    byte[] attached = null;
    for (int i = 0; i < root.getCount(); i++) {
      var part = root.getBodyPart(i);
      if (part.getFileName() != null) {
        fileNames.add(part.getFileName());
        assertThat(part.getContentType()).startsWith("application/json");
        attached = part.getInputStream().readAllBytes();
      }
    }
    assertThat(fileNames).containsExactly("registration-aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee.json");
    assertThat(attached).isEqualTo(json);
  }

  @Test
  void workbookHasHeaderAndStringCellsOnly() throws IOException {
    byte[] bytes = new PoiWorkbookWriter().write(List.of(student()));

    try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
      Sheet sheet = wb.getSheet("Registrations");
      assertThat(sheet.getPhysicalNumberOfRows()).isEqualTo(2);
      List<String> header = new ArrayList<>();
      sheet.getRow(0).forEach(c -> header.add(c.getStringCellValue()));
      assertThat(header).isEqualTo(PoiWorkbookWriter.HEADER);
      Row row = sheet.getRow(1);
      row.forEach(c -> assertThat(c.getCellType()).isEqualTo(CellType.STRING));
      assertThat(row.getCell(3).getStringCellValue()).isEqualTo("<b>Luka</b>");
      assertThat(row.getCell(6).getStringCellValue()).isEmpty();
      assertThat(row.getCell(8).getStringCellValue()).isEqualTo("=SUM(A1)");
      assertThat(row.getCell(10).getStringCellValue()).isEqualTo("WS");
      assertThat(row.getCell(11).getStringCellValue()).isEqualTo("EV");
      assertThat(row.getCell(12).getStringCellValue()).isEqualTo("M1; M2");
      assertThat(row.getCell(13).getStringCellValue()).isEqualTo("OT");
      assertThat(row.getCell(14).getStringCellValue())
          .isEqualTo("privacy (2026-09-30T10:00:00Z); news (2026-09-30T10:00:00Z)");
    }
  }

  @Test
  void emptyExportHasOnlyTheHeader() throws IOException {
    byte[] bytes = new PoiWorkbookWriter().write(List.of());

    try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
      assertThat(wb.getSheet("Registrations").getPhysicalNumberOfRows()).isEqualTo(1);
    }
  }
}

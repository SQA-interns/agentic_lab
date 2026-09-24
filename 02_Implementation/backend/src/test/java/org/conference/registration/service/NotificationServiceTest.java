package org.conference.registration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.mail.Address;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import org.conference.registration.domain.OptionCategory;
import org.conference.registration.domain.RegistrationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

class NotificationServiceTest {

  private final JavaMailSender sender = mock(JavaMailSender.class);
  private final NotificationService service =
      new NotificationService(sender, TestProperties.defaults());
  private final BackupDocument doc =
      new BackupDocument(
          1,
          UUID.randomUUID(),
          RegistrationType.EXTERNAL,
          Instant.parse("2026-05-01T10:00:00Z"),
          new BackupDocument.Participant(
              "Žiga", "Čeh", "ziga@example.si", "<b>FERI</b>", null, null, null),
          true,
          List.of(new BackupDocument.Option("ws-a", OptionCategory.WORKSHOP, "Workshop A")));
  private final byte[] json = "{\"id\":\"x\"}".getBytes(StandardCharsets.UTF_8);

  @BeforeEach
  void setUp() {
    when(sender.createMimeMessage())
        .thenAnswer(inv -> new MimeMessage(Session.getInstance(new Properties())));
  }

  @Test
  void sendsParticipantConfirmationAndOrganizerNotification() throws Exception {
    service.onRegistrationCompleted(new RegistrationCompletedEvent(doc, json));

    ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
    verify(sender, times(2)).send(sent.capture());
    MimeMessage participant = sent.getAllValues().get(0);
    MimeMessage organizer = sent.getAllValues().get(1);

    assertThat(addresses(participant.getAllRecipients())).containsExactly("ziga@example.si");
    assertThat(participant.getSubject()).contains("Registration confirmed");
    String participantText = (String) participant.getContent();
    assertThat(participantText).contains("Žiga Čeh").contains(doc.id().toString());
    participant.saveChanges(); // JavaMail sets the Content-Type header on save (done by send)
    assertThat(participant.getContentType())
        .containsIgnoringCase("text/plain")
        .containsIgnoringCase("UTF-8");

    assertThat(addresses(organizer.getAllRecipients()))
        .containsExactly("org1@conference.test", "org2@conference.test");
    organizer.saveChanges();
    List<Part> parts = parts((Multipart) organizer.getContent());
    Part attachment =
        parts.stream()
            .filter(p -> "application/json".equals(baseType(p)))
            .findFirst()
            .orElseThrow();
    assertThat(attachment.getFileName()).isEqualTo("registration-" + doc.id() + ".json");
    assertThat(attachment.getInputStream().readAllBytes()).isEqualTo(json);
  }

  @Test
  void organizerBodyIsPlainTextAndContainsAllFields() {
    String body = service.organizerBody(doc);
    assertThat(body)
        .contains("First name: Žiga")
        .contains("Organization / institution: <b>FERI</b>")
        .contains("Selected options: Workshop A")
        .doesNotContain("Student ID");
  }

  @Test
  void participantBodyListsNoneWhenNoOptions() {
    BackupDocument noOptions =
        new BackupDocument(
            1,
            doc.id(),
            RegistrationType.STUDENT,
            doc.createdAt(),
            doc.participant(),
            true,
            List.of());
    assertThat(service.participantBody(noOptions))
        .contains("Selected options: none")
        .contains("Student");
  }

  @Test
  void mailFailureIsSwallowedAndSecondMailIsStillAttempted() {
    doThrow(new MailSendException("smtp down")).when(sender).send(any(MimeMessage.class));
    service.onRegistrationCompleted(new RegistrationCompletedEvent(doc, json));
    verify(sender, times(2)).send(any(MimeMessage.class));
  }

  private static List<String> addresses(Address[] addresses) {
    return Arrays.stream(addresses).map(Address::toString).toList();
  }

  private static String baseType(Part part) {
    try {
      return part.getContentType().split(";")[0].trim().toLowerCase(java.util.Locale.ROOT);
    } catch (jakarta.mail.MessagingException e) {
      throw new IllegalStateException(e);
    }
  }

  private static List<Part> parts(Multipart multipart) throws Exception {
    List<Part> result = new ArrayList<>();
    for (int i = 0; i < multipart.getCount(); i++) {
      Part part = multipart.getBodyPart(i);
      if (part.getContent() instanceof Multipart nested) {
        result.addAll(parts(nested));
      } else {
        result.add(part);
      }
    }
    return result;
  }
}

package si.konferenca.registration.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * US-004 — Registration confirmation (API side). The in-application confirmation screen is covered
 * by frontend/e2e/registration.spec.ts.
 */
class ConfirmationAcceptanceTest extends AcceptanceTestBase {

  @Test
  void ac_004_01_acceptedRegistrationReturnsConfirmationData() {
    String email = uniqueEmail("ac00401");
    Resp r = register(validExternal(email, "ws-ai"));

    assertThat(r.status()).as(r.text()).isEqualTo(201);
    assertThat(r.header("Content-Type")).contains("application/json");
    assertThat(r.json().path("registrationId").asText()).isNotBlank();
    assertThat(r.json().path("submittedAt").asText()).isNotBlank();
    assertThat(r.json().path("options").get(0).path("name").asText())
        .isEqualTo("Delavnica: umetna inteligenca");
  }

  @Test
  void ac_004_02_rejectedSubmissionReturnsReasonsAndNoRegistration() {
    String email = uniqueEmail("ac00402");
    ObjectNode body = validExternal(email);
    body.put("firstName", "");
    body.put("personalDataConsent", false);

    Resp r = register(body);

    assertThat(r.status()).isEqualTo(400);
    assertThat(r.json().path("registrationId").isMissingNode()).isTrue();
    assertThat(r.json().path("code").asText()).isEqualTo("VALIDATION_FAILED");
    assertRejectedWithFieldError(r, "firstName", "REQUIRED");
    assertRejectedWithFieldError(r, "personalDataConsent", "CONSENT_REQUIRED");
    assertThat(countRegistrationsByEmail(email)).isZero();
  }

  @Test
  void ac_004_02_failedRecaptchaIsRejectedWithoutConfirmation() {
    String email = uniqueEmail("ac00402b");
    ObjectNode body = validExternal(email);
    body.put("recaptchaToken", "test-fail");

    Resp r = register(body);

    assertThat(r.status()).isEqualTo(400);
    assertThat(r.json().path("code").asText()).isEqualTo("RECAPTCHA_FAILED");
    assertThat(countRegistrationsByEmail(email)).isZero();
  }

  /**
   * Storage failure is induced from outside by replacing the backup directory with a plain file, so
   * the JSON backup (a required part of storing, specification §10) cannot be written.
   */
  @Test
  void ac_004_03_noConfirmationWhenRegistrationCannotBeStored() throws IOException {
    Path parked = BACKUP_DIR.resolveSibling("backups-parked");
    Files.move(BACKUP_DIR, parked);
    Files.writeString(BACKUP_DIR, "not a directory");
    String email = uniqueEmail("ac00403");
    try {
      Resp r = register(validExternal(email));

      assertThat(r.status()).as(r.text()).isEqualTo(500);
      assertThat(r.header("Content-Type")).contains("application/problem+json");
      assertThat(r.json().path("code").asText()).isEqualTo("REGISTRATION_NOT_SAVED");
      assertThat(r.json().path("registrationId").isMissingNode()).isTrue();
      assertThat(r.text()).doesNotContain("Exception").doesNotContain("at si.konferenca");
      assertThat(countRegistrationsByEmail(email)).isZero();
    } finally {
      Files.delete(BACKUP_DIR);
      Files.move(parked, BACKUP_DIR);
    }
    try (Stream<Path> files = Files.list(BACKUP_DIR)) {
      assertThat(files.map(p -> p.getFileName().toString())).noneMatch(n -> n.endsWith(".tmp"));
    }
  }
}

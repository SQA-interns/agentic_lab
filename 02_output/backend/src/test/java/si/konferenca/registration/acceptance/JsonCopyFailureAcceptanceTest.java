package si.konferenca.registration.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import si.konferenca.registration.RegistrationApplication;
import si.konferenca.registration.acceptance.support.Api;
import si.konferenca.registration.acceptance.support.Db;
import si.konferenca.registration.acceptance.support.Fixtures;
import si.konferenca.registration.acceptance.support.Mailpit;
import si.konferenca.registration.acceptance.support.TestInfrastructure;

/** AC-005-03, AR-05: no success and no database row when the JSON copy cannot be written. */
@SpringBootTest(
    classes = RegistrationApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class JsonCopyFailureAcceptanceTest {

  /** A regular file used as the copy "directory", so every copy write fails. */
  private static final Path NOT_A_DIRECTORY = createFile();

  @LocalServerPort int port;

  private Api api;
  private final Db db = TestInfrastructure.db();
  private final Mailpit mailpit = TestInfrastructure.mailpit();

  @DynamicPropertySource
  static void configuration(DynamicPropertyRegistry registry) {
    TestInfrastructure.register(
        registry, Map.of("app.json-copy-dir", NOT_A_DIRECTORY.resolve("copies").toString()));
  }

  @BeforeEach
  void createClient() {
    api = new Api(port);
  }

  @Test
  @DisplayName("AC-005-03 a failed JSON copy gives an error and leaves no database row")
  void ac00503FailedJsonCopyRollsBack() throws InterruptedException {
    String email = Fixtures.uniqueEmail();

    Api.Response r = api.register(Fixtures.external(email));

    assertThat(r.status()).as(r.text()).isGreaterThanOrEqualTo(500);
    assertThat(r.text())
        .doesNotContain("Exception")
        .doesNotContain("si.konferenca")
        .doesNotContain(NOT_A_DIRECTORY.toString());
    assertThat(r.text()).doesNotContain("\"reference\"");
    assertThat(db.countRegistrations(email)).isZero();
    Thread.sleep(2000);
    assertThat(mailpit.messagesTo(email)).isEmpty();
  }

  @Test
  @DisplayName("NFR-04 readiness is DOWN while the JSON copy directory is not writable")
  void nfr04ReadinessReportsUnwritableCopyDirectory() {
    Api.Response readiness = api.get("/actuator/health/readiness");
    Api.Response liveness = api.get("/actuator/health/liveness");

    assertThat(readiness.status()).as(readiness.text()).isEqualTo(503);
    assertThat(readiness.text()).doesNotContain(NOT_A_DIRECTORY.toString());
    assertThat(liveness.status()).isEqualTo(200);
  }

  private static Path createFile() {
    try {
      return Files.createTempFile("not-a-directory", ".txt");
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}

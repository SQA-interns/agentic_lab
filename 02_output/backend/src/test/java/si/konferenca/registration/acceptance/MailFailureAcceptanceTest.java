package si.konferenca.registration.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static si.konferenca.registration.acceptance.support.Registrations.external;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import si.konferenca.registration.RegistrationApplication;
import si.konferenca.registration.acceptance.support.AcceptanceCriterionNames;
import si.konferenca.registration.acceptance.support.ApiClient;
import si.konferenca.registration.acceptance.support.CopyStore;
import si.konferenca.registration.acceptance.support.Database;
import si.konferenca.registration.acceptance.support.TestStack;
import tools.jackson.databind.node.ObjectNode;

/** AC-006-04 and AC-007-05: an application whose SMTP server cannot be reached. */
@SpringBootTest(
    classes = RegistrationApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayNameGeneration(AcceptanceCriterionNames.class)
class MailFailureAcceptanceTest {

  private static final Path COPY_DIR = TestStack.newTempDirectory("registration-copies-no-smtp");

  @LocalServerPort private int port;

  private final Database db = TestStack.database();
  private final CopyStore copies = new CopyStore(COPY_DIR);

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    TestStack.register(
        registry,
        Map.of(
            "SMTP_HOST", "127.0.0.1",
            "SMTP_PORT", closedPort(),
            "JSON_COPY_DIR", COPY_DIR.toString()));
  }

  @BeforeEach
  void resetState() {
    copies.clear();
    db.clear();
  }

  @Test
  void ac_006_04_registrationStaysAcceptedWhenTheParticipantEmailCannotBeSent() {
    assertAcceptedAndStoredWithoutMail();
  }

  @Test
  void ac_007_05_registrationStaysAcceptedWhenTheOrganizerEmailCannotBeSent() {
    assertAcceptedAndStoredWithoutMail();
  }

  private void assertAcceptedAndStoredWithoutMail() {
    ObjectNode request = external();

    ApiClient.Response response = new ApiClient(port).register(request);

    assertThat(response.status()).as(response.text()).isEqualTo(201);
    String id = response.json().path("registrationId").asString();
    assertThat(db.registrationByEmail(request.path("email").asString())).isPresent();
    assertThat(copies.fileNames()).containsExactly("registration-" + id + ".json");
  }

  /** A local port with nothing listening on it. */
  private static int closedPort() {
    try (ServerSocket socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}

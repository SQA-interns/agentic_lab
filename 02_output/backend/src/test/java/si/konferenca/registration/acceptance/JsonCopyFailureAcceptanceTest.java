package si.konferenca.registration.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static si.konferenca.registration.acceptance.support.Payloads.external;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import si.konferenca.registration.acceptance.support.AcceptanceTest;
import si.konferenca.registration.acceptance.support.Api;
import si.konferenca.registration.acceptance.support.Checks;
import si.konferenca.registration.acceptance.support.CustomContextTest;
import si.konferenca.registration.acceptance.support.Database;
import si.konferenca.registration.acceptance.support.TestEnvironment;

/** US-005 / AR-05: no success and no database row when the JSON copy cannot be written. */
class JsonCopyFailureAcceptanceTest extends CustomContextTest {

  /** A regular file used as the JSON copy directory, so no copy can be written below it. */
  private static final Path NOT_A_DIRECTORY = blockingFile();

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    Map<String, String> p = TestEnvironment.baseProperties();
    p.put("app.json-copy-dir", NOT_A_DIRECTORY.resolve("copies").toString());
    AcceptanceTest.register(registry, p);
  }

  private static Path blockingFile() {
    try {
      Path dir = TestEnvironment.newTempDir("blocked");
      return Files.writeString(dir.resolve("not-a-directory"), "x");
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Test
  @DisplayName("AC-005-03 a failed JSON copy gives a generic 500 and leaves no database row")
  void ac005_03_failedCopyRollsBack() {
    Map<String, Object> payload = external();
    String email = (String) payload.get("email");

    Api.Response response = api.register(payload);

    assertThat(response.status()).as(response.text()).isEqualTo(500);
    assertThat(response.json().path("error").asString()).isEqualTo("internal_error");
    assertThat(response.json().path("message").asString()).isNotBlank();
    Checks.assertNoInternals(response);
    assertThat(response.text()).doesNotContain(NOT_A_DIRECTORY.getFileName().toString());
    assertThat(Database.countByEmail(email)).isZero();
  }
}

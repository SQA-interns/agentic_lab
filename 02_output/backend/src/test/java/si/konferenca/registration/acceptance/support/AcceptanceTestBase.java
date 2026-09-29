package si.konferenca.registration.acceptance.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import si.konferenca.registration.RegistrationApplication;

/**
 * Starts the whole backend on a random port with the default test configuration and gives tests
 * black-box access to its REST API, database, JSON copy directory and delivered emails.
 */
@SpringBootTest(
    classes = RegistrationApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AcceptanceTestBase {

  @LocalServerPort protected int port;

  protected Api api;
  protected final Mailpit mailpit = TestInfrastructure.mailpit();
  protected final Db db = TestInfrastructure.db();

  @DynamicPropertySource
  static void defaultConfiguration(DynamicPropertyRegistry registry) {
    TestInfrastructure.register(registry, Map.of());
  }

  @BeforeEach
  void createClient() {
    api = new Api(port);
  }

  protected Path jsonCopyDir() {
    return TestInfrastructure.sharedJsonCopyDir();
  }

  /** The JSON copy of a registration: {@code <dir>/<reference>.json} (specification 5). */
  protected static Path jsonCopy(Path dir, String reference) {
    return dir.resolve(reference + ".json");
  }

  /** JSON copy files in the directory that mention the text (e.g. a unique email). */
  protected static List<Path> jsonCopiesContaining(Path dir, String text) {
    if (!Files.isDirectory(dir)) {
      return List.of();
    }
    try (Stream<Path> files = Files.list(dir)) {
      return files.filter(Files::isRegularFile).filter(f -> read(f).contains(text)).toList();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  protected static String read(Path file) {
    try {
      return Files.readString(file, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** Asserts the rejection left no trace: no database row, no JSON copy and no email. */
  protected void assertNothingStored(String email) {
    assertNothingStored(email, jsonCopyDir());
  }

  protected void assertNothingStored(String email, Path copyDir) {
    assertThat(db.countRegistrations(email)).as("database rows for " + email).isZero();
    assertThat(jsonCopiesContaining(copyDir, email)).as("JSON copies for " + email).isEmpty();
    sleep(Duration.ofSeconds(1));
    assertThat(mailpit.messagesTo(email)).as("emails to " + email).isEmpty();
  }

  /** Asserts a rejection response: 400, the field error, and no internal details (ES-07). */
  protected static void assertRejected(Api.Response r, String field) {
    assertThat(r.status()).as("status, body: " + r.text()).isEqualTo(400);
    assertThat(r.errorFields()).as("field errors").contains(field);
    assertNoInternals(r);
  }

  protected static void assertNoInternals(Api.Response r) {
    assertThat(r.text())
        .doesNotContain("Exception")
        .doesNotContain("org.springframework")
        .doesNotContain("si.konferenca")
        .doesNotContain("SQL");
  }

  protected static void sleep(Duration d) {
    try {
      Thread.sleep(d.toMillis());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}

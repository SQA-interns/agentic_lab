package si.konferenca.registration.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import si.konferenca.registration.config.AppProperties;
import si.konferenca.registration.config.StartupChecksTest;
import si.konferenca.registration.domain.Registration;
import si.konferenca.registration.service.NotificationServiceTest;

class JsonCopyStoreTest {

  @TempDir Path dir;

  private JsonCopyStore store(Path directory) throws IOException {
    AppProperties p = StartupChecksTest.validProduction();
    return new JsonCopyStore(
        new AppProperties(
            p.environment(),
            p.conferenceName(),
            p.optionsFile(),
            directory.toString(),
            p.mailFrom(),
            p.recaptcha(),
            p.organizer(),
            p.corsAllowedOrigin(),
            p.rateLimit(),
            p.maxRequestBytes(),
            p.retention()));
  }

  @Test
  @DisplayName("BR-07 the copy follows registration-copy.schema.json; absent fields are omitted")
  void writesSchemaShapedDocument() throws Exception {
    Registration r = NotificationServiceTest.student();
    JsonCopyStore store = store(dir);

    store.write(r);

    String json = Files.readString(dir.resolve(r.id() + ".json"), StandardCharsets.UTF_8);
    assertThat(json)
        .startsWith(
            "{\"schemaVersion\":1,\"id\":\""
                + r.id()
                + "\",\"receivedAt\":\"2026-10-08T10:00:00Z\",\"type\":\"STUDENT\"")
        .contains("\"firstName\":\"Žiga\"")
        .contains("\"studentId\":\"6321\"")
        .contains("{\"id\":\"m2\",\"name\":\"Dinner\",\"category\":\"MEAL\"}")
        .contains("{\"id\":\"data\",\"text\":\"I agree\",\"givenAt\":\"2026-10-08T10:00:00Z\"}")
        .doesNotContain("organization")
        .doesNotContain("null");
    assertThat(store.read(r.id())).isEqualTo(Files.readAllBytes(dir.resolve(r.id() + ".json")));
  }

  @Test
  void leavesNoTemporaryFile() throws Exception {
    store(dir).write(NotificationServiceTest.student());
    try (Stream<Path> files = Files.list(dir)) {
      assertThat(files.map(p -> p.getFileName().toString())).allMatch(n -> n.endsWith(".json"));
    }
  }

  @Test
  @DisplayName("AC-005-03 a second write of the same id fails instead of overwriting")
  void refusesToOverwriteAPendingTemporaryFile() throws Exception {
    Registration r = NotificationServiceTest.student();
    JsonCopyStore store = store(dir);
    Files.writeString(dir.resolve(r.id() + ".tmp"), "other writer");

    assertThatThrownBy(() -> store.write(r)).isInstanceOf(IOException.class);
    assertThat(dir.resolve(r.id() + ".json")).doesNotExist();
  }

  @Test
  void writeFailsWhenDirectoryIsGone() throws Exception {
    Path sub = Files.createDirectory(dir.resolve("copies"));
    JsonCopyStore store = store(sub);
    Files.delete(sub);

    assertThatThrownBy(() -> store.write(NotificationServiceTest.student()))
        .isInstanceOf(IOException.class);
  }

  @Test
  void deleteReportsWhetherACopyExisted() throws Exception {
    Registration r = NotificationServiceTest.student();
    JsonCopyStore store = store(dir);
    store.write(r);

    assertThat(store.delete(r.id())).isTrue();
    assertThat(store.delete(r.id())).isFalse();
  }

  @Test
  void createsTheDirectoryAtStartup() throws Exception {
    Path nested = dir.resolve("a/b");
    store(nested);
    assertThat(nested).isDirectory();
  }
}

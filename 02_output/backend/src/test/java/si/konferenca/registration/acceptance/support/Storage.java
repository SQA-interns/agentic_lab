package si.konferenca.registration.acceptance.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/** Observes the persisted state: registration rows and JSON copy files. */
public final class Storage {

  private Storage() {}

  /** Number of JSON copy files in the shared copy directory. */
  public static long jsonCopyCount() {
    return jsonCopyCount(AcceptanceStack.jsonCopyDir());
  }

  public static long jsonCopyCount(Path dir) {
    try (Stream<Path> files = Files.list(dir)) {
      return files.filter(p -> p.getFileName().toString().endsWith(".json")).count();
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  /** Captures row and file counts so a rejection can be checked to have changed nothing. */
  public static Snapshot snapshot() {
    return new Snapshot(AcceptanceStack.db().countRegistrations(), jsonCopyCount());
  }

  /** Counts at one moment. */
  public record Snapshot(long rows, long files) {
    public void assertUnchanged() {
      Snapshot now = snapshot();
      assertThat(now.rows()).as("registration rows").isEqualTo(rows);
      assertThat(now.files()).as("JSON copy files").isEqualTo(files);
    }
  }
}

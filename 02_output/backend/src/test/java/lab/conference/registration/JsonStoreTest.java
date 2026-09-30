package lab.conference.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JsonStoreTest {

  @TempDir Path root;

  @Test
  void createsDirectoriesAndPublishesAtomically() throws Exception {
    JsonStore store = new JsonStore(root);
    UUID id = UUID.randomUUID();
    store.publish(id, "{\"a\":1}".getBytes(StandardCharsets.UTF_8));
    assertThat(store.file(id)).hasContent("{\"a\":1}");
    assertThat(root.resolve("staging")).isEmptyDirectory();
    assertThat(root.resolve("orphaned")).isDirectory();
    assertThat(store.read(id)).isEqualTo("{\"a\":1}".getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void inFlightFilesAreNeverReconciled() throws Exception {
    JsonStore store = new JsonStore(root);
    UUID id = UUID.randomUUID();
    store.publish(id, new byte[] {1});
    Instant future = Instant.now().plusSeconds(60);
    assertThat(store.settledFilesOlderThan(future)).isEmpty();
    store.quarantine(id);
    assertThat(store.file(id)).exists();
    store.settled(id);
    assertThat(store.settledFilesOlderThan(future)).containsExactly(id);
    assertThat(store.settledFilesOlderThan(Instant.now().minusSeconds(60))).isEmpty();
    store.quarantine(id);
    assertThat(store.file(id)).doesNotExist();
    assertThat(root.resolve("orphaned").resolve(id + ".json")).exists();
  }

  @Test
  void discardRemovesFileAndClearsInFlight() throws Exception {
    JsonStore store = new JsonStore(root);
    UUID id = UUID.randomUUID();
    store.publish(id, new byte[] {1});
    store.discard(id);
    assertThat(store.file(id)).doesNotExist();
    store.discard(id);
  }

  @Test
  void publishFailureLeavesNoFiles() throws Exception {
    JsonStore store = new JsonStore(root);
    UUID id = UUID.randomUUID();
    Files.setPosixFilePermissions(
        root.resolve("registrations"), PosixFilePermissions.fromString("r-x------"));
    try {
      assertThatThrownBy(() -> store.publish(id, new byte[] {1})).isInstanceOf(IOException.class);
    } finally {
      Files.setPosixFilePermissions(
          root.resolve("registrations"), PosixFilePermissions.fromString("rwx------"));
    }
    assertThat(root.resolve("staging")).isEmptyDirectory();
    assertThat(store.file(id)).doesNotExist();
    store.publish(id, new byte[] {2});
    assertThat(store.file(id)).exists();
  }

  @Test
  void duplicatePublishIsRefused() throws Exception {
    JsonStore store = new JsonStore(root);
    UUID id = UUID.randomUUID();
    Files.writeString(root.resolve("staging").resolve(id + ".json.tmp"), "x");
    assertThatThrownBy(() -> store.publish(id, new byte[] {1})).isInstanceOf(IOException.class);
  }

  @Test
  void cleanStagingRemovesOnlyOldAbandonedFiles() throws Exception {
    JsonStore store = new JsonStore(root);
    Path staging = root.resolve("staging");
    Path old = staging.resolve(UUID.randomUUID() + ".json.tmp");
    Path young = staging.resolve(UUID.randomUUID() + ".json.tmp");
    Path junk = staging.resolve("junk.tmp");
    Files.writeString(old, "x");
    Files.writeString(young, "x");
    Files.writeString(junk, "x");
    Files.setLastModifiedTime(old, FileTime.from(Instant.now().minusSeconds(3600)));
    Files.setLastModifiedTime(junk, FileTime.from(Instant.now().minusSeconds(3600)));
    assertThat(store.cleanStaging(Instant.now().minusSeconds(60))).isEqualTo(2);
    assertThat(old).doesNotExist();
    assertThat(junk).doesNotExist();
    assertThat(young).exists();
  }

  @Test
  void ignoresFilesThatAreNotRegistrationIds() throws Exception {
    JsonStore store = new JsonStore(root);
    Files.writeString(root.resolve("registrations").resolve("notes.json"), "x");
    Files.writeString(root.resolve("registrations").resolve("x.txt"), "x");
    assertThat(store.settledFilesOlderThan(Instant.now().plusSeconds(60))).isEmpty();
  }

  @Test
  void unwritableRootFailsAtStartup() throws Exception {
    Path locked = root.resolve("locked");
    Files.createDirectories(locked.resolve("registrations"));
    Files.createDirectories(locked.resolve("staging"));
    Files.createDirectories(locked.resolve("orphaned"));
    Files.setPosixFilePermissions(
        locked.resolve("staging"), PosixFilePermissions.fromString("r-x------"));
    try {
      assertThatThrownBy(() -> new JsonStore(locked)).isInstanceOf(IOException.class);
    } finally {
      Files.setPosixFilePermissions(
          locked.resolve("staging"), PosixFilePermissions.fromString("rwx------"));
    }
  }
}

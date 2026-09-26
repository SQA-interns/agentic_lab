package si.konferenca.registration.infrastructure.backup;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import si.konferenca.registration.TestFixtures;
import si.konferenca.registration.config.AppProperties;
import si.konferenca.registration.domain.Registration;

class FileRegistrationBackupTest {

  @TempDir Path tempDir;

  private FileRegistrationBackup backup(Path directory) throws IOException {
    AppProperties base = TestFixtures.properties(TestFixtures.conference());
    return new FileRegistrationBackup(
        new AppProperties(
            base.conference(),
            base.recaptcha(),
            base.mail(),
            new AppProperties.Backup(directory),
            base.organizer(),
            base.rateLimit(),
            base.request(),
            base.cors()));
  }

  @Test
  void createsDirectoryAndWritesUtf8JsonFile() throws IOException {
    Path directory = tempDir.resolve("nested/registrations");
    Registration registration = TestFixtures.externalRegistration();
    UUID id = UUID.fromString("11111111-2222-3333-4444-555555555555");
    ReflectionTestUtils.setField(registration, "id", id);

    backup(directory).write(registration, "{\"lastName\":\"Šušteršič\"}");

    List<Path> files;
    try (Stream<Path> list = Files.list(directory)) {
      files = list.toList();
    }
    assertThat(files).hasSize(1);
    Path file = files.getFirst();
    assertThat(file.getFileName().toString())
        .isEqualTo("20260301T101530Z_11111111-2222-3333-4444-555555555555.json");
    assertThat(Files.readString(file, StandardCharsets.UTF_8))
        .isEqualTo("{\"lastName\":\"Šušteršič\"}");
  }

  @Test
  void fileNameContainsNoPersonalData() throws IOException {
    Registration registration = TestFixtures.externalRegistration();
    ReflectionTestUtils.setField(registration, "id", UUID.randomUUID());
    backup(tempDir).write(registration, "{}");
    try (Stream<Path> list = Files.list(tempDir)) {
      assertThat(list.map(p -> p.getFileName().toString()))
          .allSatisfy(
              name ->
                  assertThat(name)
                      .doesNotContain("Žiga")
                      .doesNotContain("ziga")
                      .doesNotContain(".tmp"));
    }
  }
}

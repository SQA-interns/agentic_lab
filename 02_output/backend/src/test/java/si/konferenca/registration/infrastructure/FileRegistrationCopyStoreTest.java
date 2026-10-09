package si.konferenca.registration.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import si.konferenca.registration.application.RegistrationCopyStore;
import si.konferenca.registration.domain.Category;
import si.konferenca.registration.domain.GivenConsent;
import si.konferenca.registration.domain.ParticipantDetails;
import si.konferenca.registration.domain.Registration;
import si.konferenca.registration.domain.RegistrationType;
import si.konferenca.registration.domain.SelectedOption;

class FileRegistrationCopyStoreTest {

  @TempDir Path dir;

  static Registration student() {
    Instant at = Instant.parse("2026-10-09T08:15:30.123Z");
    return new Registration(
        UUID.fromString("3f1c2a9e-8d4b-4c6a-9b1e-2f7d5e8a1c40"),
        RegistrationType.STUDENT,
        new ParticipantDetails("Žana", "Kovač", "zana@example.si", null, "FRI", "RI", "6320"),
        at,
        List.of(new SelectedOption("ws-ai", "Delavnica", Category.WORKSHOP)),
        List.of(new GivenConsent("data", "I agree.", at)));
  }

  @Test
  void writesTheContractShapeInUtf8AndReturnsTheSameBytes() throws IOException {
    FileRegistrationCopyStore store = new FileRegistrationCopyStore(dir);

    byte[] returned = store.write(student());

    Path file = dir.resolve("registration-3f1c2a9e-8d4b-4c6a-9b1e-2f7d5e8a1c40.json");
    assertThat(Files.readAllBytes(file)).isEqualTo(returned);
    assertThat(new String(returned, StandardCharsets.UTF_8))
        .isEqualTo(
            "{\"registrationId\":\"3f1c2a9e-8d4b-4c6a-9b1e-2f7d5e8a1c40\","
                + "\"registeredAt\":\"2026-10-09T08:15:30.123Z\",\"type\":\"STUDENT\","
                + "\"participant\":{\"firstName\":\"Žana\",\"lastName\":\"Kovač\","
                + "\"email\":\"zana@example.si\",\"studyInstitution\":\"FRI\","
                + "\"studyProgramme\":\"RI\",\"studentId\":\"6320\"},"
                + "\"options\":[{\"id\":\"ws-ai\",\"name\":\"Delavnica\",\"category\":\"workshop\"}],"
                + "\"consents\":[{\"id\":\"data\",\"text\":\"I agree.\","
                + "\"givenAt\":\"2026-10-09T08:15:30.123Z\"}]}");
    try (Stream<Path> files = Files.list(dir)) {
      assertThat(files).hasSize(1);
    }
  }

  @Test
  void deleteRemovesTheCopyAndIgnoresMissingOnes() {
    FileRegistrationCopyStore store = new FileRegistrationCopyStore(dir);
    Registration registration = student();
    store.write(registration);

    store.delete(registration.id());
    store.delete(registration.id());

    assertThat(dir.resolve("registration-" + registration.id() + ".json")).doesNotExist();
  }

  @Test
  void createsAMissingDirectory() {
    Path nested = dir.resolve("a").resolve("b");

    new FileRegistrationCopyStore(nested).write(student());

    assertThat(nested).isDirectory();
  }

  @Test
  void refusesAPathThatIsAFile() throws IOException {
    Path file = Files.writeString(dir.resolve("plain"), "x");

    assertThatThrownBy(() -> new FileRegistrationCopyStore(file))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("JSON_COPY_DIR");
  }

  @Test
  void failedWriteLeavesNoFileBehind() throws IOException {
    FileRegistrationCopyStore store = new FileRegistrationCopyStore(dir);
    Files.delete(dir);
    Files.writeString(dir, "now a file");

    assertThatThrownBy(() -> store.write(student()))
        .isInstanceOf(RegistrationCopyStore.CopyStoreException.class)
        .hasMessageContaining("3f1c2a9e-8d4b-4c6a-9b1e-2f7d5e8a1c40");
    Files.delete(dir);
    Files.createDirectory(dir);
  }
}

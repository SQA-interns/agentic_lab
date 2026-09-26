package si.konferenca.registration.infrastructure.backup;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;
import si.konferenca.registration.application.port.RegistrationBackup;
import si.konferenca.registration.config.AppProperties;
import si.konferenca.registration.domain.Registration;

/** Writes one raw JSON file per accepted registration to persistent filesystem storage. */
@Component
public class FileRegistrationBackup implements RegistrationBackup {

  private static final DateTimeFormatter FILE_TIMESTAMP =
      DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

  private final Path directory;

  public FileRegistrationBackup(AppProperties properties) throws IOException {
    this.directory = properties.backup().directory().toAbsolutePath().normalize();
    Files.createDirectories(directory);
  }

  @Override
  public void write(Registration registration, String json) throws IOException {
    String name =
        FILE_TIMESTAMP.format(registration.getCreatedAt()) + "_" + registration.getId() + ".json";
    Path target = directory.resolve(name);
    Path temp = directory.resolve(name + ".tmp");
    Files.writeString(temp, json, StandardCharsets.UTF_8);
    try {
      Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
    } catch (IOException e) {
      Files.deleteIfExists(temp);
      throw e;
    }
  }
}

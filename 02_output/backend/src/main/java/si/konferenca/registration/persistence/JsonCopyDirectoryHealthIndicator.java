package si.konferenca.registration.persistence;

import java.io.IOException;
import org.springframework.boot.health.contributor.AbstractHealthIndicator;
import org.springframework.boot.health.contributor.Health;
import org.springframework.stereotype.Component;

/** Readiness: the JSON copy directory accepts new files (NFR-04, ES-09). */
@Component("jsonCopyDirectory")
public class JsonCopyDirectoryHealthIndicator extends AbstractHealthIndicator {

  private final JsonCopyStore store;

  public JsonCopyDirectoryHealthIndicator(JsonCopyStore store) {
    super("JSON copy directory check failed");
    this.store = store;
  }

  @Override
  protected void doHealthCheck(Health.Builder builder) {
    try {
      store.checkWritable();
      builder.up();
    } catch (IOException e) {
      builder.down();
    }
  }
}

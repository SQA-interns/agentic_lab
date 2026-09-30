package si.konferenca.registration.infrastructure;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import si.konferenca.registration.application.JsonCopyStore;

/** Readiness: registrations can only be accepted while JSON copies can be written (ES-09). */
public class JsonCopyDirectoryHealthIndicator implements HealthIndicator {

  private final JsonCopyStore copies;

  public JsonCopyDirectoryHealthIndicator(JsonCopyStore copies) {
    this.copies = copies;
  }

  @Override
  public Health health() {
    return copies.writable() ? Health.up().build() : Health.down().build();
  }
}

package si.konferenca.registration.acceptance.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import si.konferenca.registration.RegistrationApplication;

/**
 * Starts the backend on a random port against {@link TestEnvironment} and talks to it only over
 * HTTP. Each test class declares a {@code @DynamicPropertySource} method that calls {@link
 * #configure} with its overrides.
 */
@SpringBootTest(
    classes = RegistrationApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AcceptanceTestBase {

  @Value("${local.server.port}")
  private int port;

  protected Api api;

  protected static void configure(DynamicPropertyRegistry registry, Map<String, String> overrides) {
    Map<String, String> properties = TestEnvironment.properties();
    properties.putAll(overrides);
    properties.forEach((key, value) -> registry.add(key, () -> value));
  }

  @BeforeEach
  void resetState() {
    api = new Api(port);
    Database.removeFaults();
    Database.reset();
    JsonCopies.reset();
    Mailpit.deleteAll();
  }

  @AfterEach
  void removeInjectedFaults() {
    Database.removeFaults();
    JsonCopies.reset();
  }

  /** Asserts that a rejected request left no registration, JSON copy or file behind. */
  protected static void assertNothingStored() {
    assertThat(Database.registrationCount()).as("stored registrations").isZero();
    assertThat(JsonCopies.files()).as("JSON copies").isEmpty();
  }
}

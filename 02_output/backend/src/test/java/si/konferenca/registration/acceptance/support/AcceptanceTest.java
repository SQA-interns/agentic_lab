package si.konferenca.registration.acceptance.support;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import si.konferenca.registration.RegistrationApplication;

/**
 * Base for acceptance tests that run against the default test configuration: the backend on a
 * random port with PostgreSQL and Mailpit containers, reCAPTCHA test mode and the test options
 * file. Tests use only the public interfaces (HTTP, SQL schema, JSON copy directory, SMTP).
 */
@SpringBootTest(
    classes = RegistrationApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AcceptanceTest {

  @LocalServerPort protected int port;

  protected Api api;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    register(registry, TestEnvironment.baseProperties());
  }

  public static void register(DynamicPropertyRegistry registry, Map<String, String> properties) {
    properties.forEach((k, v) -> registry.add(k, () -> v));
  }

  @BeforeEach
  void createClient() {
    api = new Api(port);
  }

  protected Path jsonDir() {
    return TestEnvironment.defaultJsonDir();
  }
}

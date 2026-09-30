package si.konferenca.registration.acceptance.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import si.konferenca.registration.RegistrationApplication;

/**
 * Base for acceptance tests that need their own configuration. Subclasses declare one
 * {@code @DynamicPropertySource} method that starts from {@link TestEnvironment#baseProperties()}.
 */
@SpringBootTest(
    classes = RegistrationApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class CustomContextTest {

  @LocalServerPort protected int port;

  protected Api api;

  @BeforeEach
  void createClient() {
    api = new Api(port);
  }
}

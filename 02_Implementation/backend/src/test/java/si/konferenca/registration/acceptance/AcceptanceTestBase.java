package si.konferenca.registration.acceptance;

import java.util.Map;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Standard acceptance configuration: the harness defaults with no overrides. */
public abstract class AcceptanceTestBase extends AcceptanceHarness {

  @DynamicPropertySource
  static void acceptanceProperties(DynamicPropertyRegistry registry) {
    registerProperties(registry, Map.of());
  }
}

package si.konferenca.registration.acceptance.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import si.konferenca.registration.RegistrationApplication;
import tools.jackson.databind.JsonNode;

/**
 * Base of the acceptance tests: the application on a random port against {@link TestStack}, with
 * empty tables, copy directory and mailbox before each test.
 */
@SpringBootTest(
    classes = RegistrationApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayNameGeneration(AcceptanceCriterionNames.class)
public abstract class AcceptanceTest {

  @LocalServerPort private int port;

  protected ApiClient api;
  protected final Database db = TestStack.database();
  protected final Mailpit mail = TestStack.mailpit();
  protected final CopyStore copies = new CopyStore(TestStack.copyDir());

  @DynamicPropertySource
  static void stackProperties(DynamicPropertyRegistry registry) {
    TestStack.register(registry, Map.of());
  }

  @BeforeEach
  void resetState() {
    api = new ApiClient(port);
    copies.clear();
    db.clear();
    mail.clear();
  }

  /** Asserts a 400 validation_failed response with an error of {@code code} on {@code field}. */
  protected static void assertFieldError(ApiClient.Response response, String field, String code) {
    assertThat(response.status()).as("status of %s", response.text()).isEqualTo(400);
    JsonNode body = response.json();
    assertThat(body.path("error").asString()).isEqualTo("validation_failed");
    assertThat(fieldErrors(body)).contains(field + ":" + code);
  }

  /** "field:code" of every field error in an error body. */
  protected static List<String> fieldErrors(JsonNode body) {
    List<String> errors = new ArrayList<>();
    for (JsonNode error : body.path("fieldErrors")) {
      errors.add(error.path("field").asString() + ":" + error.path("code").asString());
    }
    return errors;
  }

  /** Asserts that nothing at all was stored and no email was sent. */
  protected void assertNothingStoredOrSent() {
    assertThat(db.countRegistrations()).isZero();
    assertThat(copies.fileNames()).isEmpty();
    assertNoEmail();
  }

  protected void assertNoEmail() {
    try {
      Thread.sleep(Duration.ofMillis(500));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    assertThat(mail.all()).isEmpty();
  }
}

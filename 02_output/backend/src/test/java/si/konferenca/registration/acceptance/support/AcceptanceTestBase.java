package si.konferenca.registration.acceptance.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import si.konferenca.registration.RegistrationApplication;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/** Starts the application once on a random port against the shared acceptance environment. */
@SpringBootTest(
    classes = RegistrationApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class AcceptanceTestBase {

  protected static final Duration MAIL_WAIT = Duration.ofSeconds(15);

  protected final Database database = AcceptanceEnvironment.database();
  protected final Mailpit mailpit = AcceptanceEnvironment.mailpit();
  protected final JsonCopies jsonCopies = AcceptanceEnvironment.jsonCopies();
  protected ApiClient api;

  @Autowired private Environment environment;

  @DynamicPropertySource
  static void configure(DynamicPropertyRegistry registry) {
    AcceptanceEnvironment.properties().forEach((key, value) -> registry.add(key, () -> value));
  }

  @BeforeEach
  void connect() {
    api = new ApiClient(environment.getProperty("local.server.port", Integer.class));
  }

  /** Submits a registration and asserts that it was accepted; returns the response body. */
  protected JsonNode registerAccepted(ObjectNode registration) {
    ApiClient.Response response = api.register(registration);
    assertThat(response.status()).as("status of %s", response.text()).isEqualTo(201);
    return response.json();
  }

  protected static UUID idOf(JsonNode accepted) {
    return UUID.fromString(accepted.get("id").asString());
  }

  /** Asserts a 400 validation response that names the field with the code. */
  protected static void assertFieldError(ApiClient.Response response, String field, String code) {
    assertThat(response.status()).as("status of %s", response.text()).isEqualTo(400);
    assertThat(response.contentType()).startsWith("application/problem+json");
    JsonNode body = response.json();
    assertThat(body.get("code").asString()).isEqualTo("VALIDATION_FAILED");
    List<String> errors =
        body.get("errors")
            .valueStream()
            .map(e -> e.get("field").asString() + ":" + e.get("code").asString())
            .toList();
    assertThat(errors).contains(field + ":" + code);
  }

  /** Asserts that nothing exists for the address: no row, no JSON copy, no email. */
  protected void assertNothingStoredFor(String email) {
    assertThat(database.countByEmail(email)).as("registrations stored for %s", email).isZero();
    assertThat(jsonCopies.anyContains(email.strip())).as("JSON copy for %s", email).isFalse();
    Mailpit.settle();
    assertThat(mailpit.messagesTo(email.strip())).as("emails to %s", email).isEmpty();
  }
}

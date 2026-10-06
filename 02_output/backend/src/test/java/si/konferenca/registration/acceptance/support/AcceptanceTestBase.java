package si.konferenca.registration.acceptance.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import si.konferenca.registration.RegistrationApplication;
import tools.jackson.databind.JsonNode;

/**
 * Runs the backend on a random port with the shared acceptance environment. Tests use only the
 * public interfaces: REST API, database contract, JSON copy directory and delivered emails.
 */
@SpringBootTest(
    classes = RegistrationApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AcceptanceTestBase {

  @DynamicPropertySource
  static void acceptanceSettings(DynamicPropertyRegistry registry) {
    AcceptanceEnvironment.baseSettings().forEach((key, value) -> registry.add(key, () -> value));
  }

  @Autowired private Environment environment;

  protected Api api;
  protected final Db db = new Db();
  protected final Mailpit mail = new Mailpit();
  protected final JsonCopies copies = new JsonCopies(AcceptanceEnvironment.JSON_COPY_DIR);

  @BeforeEach
  void resetState() {
    api = new Api(URI.create("http://127.0.0.1:" + environment.getProperty("local.server.port")));
    db.clear();
    copies.clear();
    mail.clear();
  }

  @AfterEach
  void restoreStorage() {
    copies.setWritable(true);
    db.unblockInserts();
  }

  /** A 400 VALIDATION_FAILED response with an error of the code for the field. */
  protected static void assertFieldError(Api.Response response, String field, String code) {
    assertThat(response.status()).as(response.toString()).isEqualTo(400);
    JsonNode body = response.json();
    assertThat(body.path("code").asString()).isEqualTo("VALIDATION_FAILED");
    boolean found = false;
    for (JsonNode error : body.path("fieldErrors")) {
      if (field.equals(error.path("field").asString())
          && code.equals(error.path("code").asString())) {
        found = true;
      }
    }
    assertThat(found).as("field error %s/%s in %s", field, code, response).isTrue();
  }

  /** An error response with the status and code of `openapi.yaml`. */
  protected static void assertError(Api.Response response, int status, String code) {
    assertThat(response.status()).as(response.toString()).isEqualTo(status);
    assertThat(response.json().path("code").asString()).isEqualTo(code);
  }

  /** Nothing was stored: no database row and no raw JSON copy. */
  protected void assertNothingStored() {
    assertThat(db.countRegistrations()).as("registrations in the database").isZero();
    assertThat(copies.files()).as("files in the JSON copy directory").isEmpty();
  }

  /** A 201 response; returns its body. */
  protected static JsonNode assertAccepted(Api.Response response) {
    assertThat(response.status()).as(response.toString()).isEqualTo(201);
    return response.json();
  }
}

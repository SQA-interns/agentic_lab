package si.konferenca.registration.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static si.konferenca.registration.acceptance.support.Registrations.external;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import si.konferenca.registration.RegistrationApplication;
import si.konferenca.registration.acceptance.support.AcceptanceCriterionNames;
import si.konferenca.registration.acceptance.support.ApiClient;
import si.konferenca.registration.acceptance.support.CopyStore;
import si.konferenca.registration.acceptance.support.Mailpit;
import si.konferenca.registration.acceptance.support.TestStack;

/** US-005 AC-005-04: an application whose own database goes away while it runs. */
@SpringBootTest(
    classes = RegistrationApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext
@DisplayNameGeneration(AcceptanceCriterionNames.class)
class DatabaseUnavailableAcceptanceTest {

  private static final PostgreSQLContainer DATABASE =
      new PostgreSQLContainer(DockerImageName.parse("postgres:16.15-alpine"));
  private static final Path COPY_DIR = TestStack.newTempDirectory("registration-copies-db-down");

  static {
    DATABASE.start();
  }

  @LocalServerPort private int port;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    TestStack.register(
        registry,
        Map.of(
            "DATABASE_URL", DATABASE.getJdbcUrl(),
            "DATABASE_USER", DATABASE.getUsername(),
            "POSTGRES_PASSWORD", DATABASE.getPassword(),
            "JSON_COPY_DIR", COPY_DIR.toString(),
            "spring.datasource.hikari.connection-timeout", "2000"));
  }

  @Test
  void ac_005_04_registrationIsNotAcceptedWhenTheDatabaseIsUnavailable() {
    ApiClient api = new ApiClient(port);
    Mailpit mail = TestStack.mailpit();
    CopyStore copies = new CopyStore(COPY_DIR);
    mail.clear();
    DATABASE.stop();

    ApiClient.Response response = api.register(external());

    assertThat(response.status()).as(response.text()).isEqualTo(500);
    assertThat(response.json().path("error").asString()).isEqualTo("internal_error");
    assertThat(copies.fileNames()).isEmpty();
    assertThat(mail.all()).isEmpty();
  }
}

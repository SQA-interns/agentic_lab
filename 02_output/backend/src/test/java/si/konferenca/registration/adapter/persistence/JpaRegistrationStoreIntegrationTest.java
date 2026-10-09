package si.konferenca.registration.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import si.konferenca.registration.application.DuplicateEmailException;
import si.konferenca.registration.domain.Field;
import si.konferenca.registration.domain.Fixtures;
import si.konferenca.registration.domain.Registration;
import si.konferenca.registration.domain.RegistrationType;

/** Persistence against PostgreSQL with the Flyway schema (BR-07, AR-06, D-18). */
@SpringBootTest
@Testcontainers
class JpaRegistrationStoreIntegrationTest {

  @Container
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16.15-alpine");

  @DynamicPropertySource
  static void settings(DynamicPropertyRegistry registry) throws Exception {
    registry.add("DATABASE_URL", POSTGRES::getJdbcUrl);
    registry.add("DATABASE_USER", POSTGRES::getUsername);
    registry.add("POSTGRES_PASSWORD", POSTGRES::getPassword);
    registry.add("APP_ENVIRONMENT", () -> "test");
    registry.add("SMTP_HOST", () -> "127.0.0.1");
    registry.add("SMTP_PORT", () -> "2525");
    registry.add("SMTP_TLS", () -> "false");
    registry.add("ORGANIZER_EMAILS", () -> "o@k.si");
    registry.add("ORGANIZER_USERNAME", () -> "organizer");
    registry.add("ORGANIZER_PASSWORD", () -> "integration-pass-0001"); // gitleaks:allow
    registry.add("RECAPTCHA_TEST_MODE", () -> "true");
    registry.add("CONFERENCE_CONFIG_PATH", () -> "../config/conference.json");
    String dir = Files.createTempDirectory("json").toString();
    registry.add("JSON_COPY_DIR", () -> dir);
  }

  @Autowired JpaRegistrationStore store;
  @Autowired TransactionTemplate transactions;

  private Registration withEmail(RegistrationType type, String email) {
    Registration base = Fixtures.registration(type);
    Map<Field, String> values = new java.util.EnumMap<>(base.values());
    values.put(Field.EMAIL, email);
    return new Registration(
        UUID.randomUUID(),
        type,
        values,
        base.selectedOptions(),
        base.consents(),
        base.submittedAt());
  }

  @Test
  void br07_registrationRoundTripsWithOptionsAndConsents() {
    Registration registration = withEmail(RegistrationType.STUDENT, "rt@example.si");

    transactions.executeWithoutResult(s -> store.insert(registration));

    Registration loaded =
        store.findAll().stream()
            .filter(r -> r.id().equals(registration.id()))
            .findFirst()
            .orElseThrow();
    assertThat(loaded.values()).isEqualTo(registration.values());
    assertThat(loaded.optionNames(si.konferenca.registration.domain.Category.WORKSHOP))
        .containsExactly("Workshop A");
    assertThat(loaded.consents()).isEqualTo(registration.consents());
    assertThat(store.existsByNormalizedEmail("rt@example.si")).isTrue();
  }

  @Test
  void d18_secondInsertWithTheSameNormalizedEmailIsADuplicate() {
    transactions.executeWithoutResult(
        s -> store.insert(withEmail(RegistrationType.EXTERNAL, "Dup@Example.si")));

    assertThatThrownBy(
            () ->
                transactions.executeWithoutResult(
                    s -> store.insert(withEmail(RegistrationType.STUDENT, "dup@example.si "))))
        .isInstanceOf(DuplicateEmailException.class);
  }

  @Test
  void br01_schemaRefusesFieldsOfTheWrongType() {
    Registration external = withEmail(RegistrationType.EXTERNAL, "mixed@example.si");
    Map<Field, String> values = new java.util.EnumMap<>(external.values());
    values.put(Field.STUDENT_ID, "E1");
    Registration mixed =
        new Registration(
            external.id(),
            RegistrationType.EXTERNAL,
            values,
            List.of(),
            List.of(),
            external.submittedAt());

    assertThatThrownBy(() -> transactions.executeWithoutResult(s -> store.insert(mixed)))
        .isNotInstanceOf(DuplicateEmailException.class)
        .hasRootCauseInstanceOf(org.postgresql.util.PSQLException.class);
  }

  @Test
  void ac00801_findAllIsOrderedBySubmissionTime() {
    List<Registration> all = store.findAll();

    for (int i = 1; i < all.size(); i++) {
      assertThat(all.get(i).submittedAt()).isAfterOrEqualTo(all.get(i - 1).submittedAt());
    }
  }
}

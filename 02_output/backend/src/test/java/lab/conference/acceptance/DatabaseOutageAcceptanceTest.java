package lab.conference.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Map;
import lab.conference.acceptance.support.Api;
import lab.conference.acceptance.support.AppInstance;
import lab.conference.acceptance.support.Infra;
import lab.conference.acceptance.support.Mailpit;
import lab.conference.acceptance.support.Payloads;
import lab.conference.acceptance.support.Workbook;
import org.junit.jupiter.api.Test;

/** US-005: no acceptance while the database is unavailable (AC-005-03). */
class DatabaseOutageAcceptanceTest {

  @Test
  void ac_005_03_databaseOutageGivesServerErrorAndNoAcceptedRegistration() {
    var postgres = Infra.newPostgresOnFixedPort(Infra.freePort());
    postgres.start();
    try {
      Infra.DatabaseRef db = Infra.newDatabase(postgres);
      try (AppInstance app = AppInstance.builder().database(db).build().start()) {
        Map<String, Object> body = Payloads.external();
        String email = (String) body.get("email");

        postgres.getDockerClient().stopContainerCmd(postgres.getContainerId()).exec();
        Api.Response r = app.api().postExternal(body);
        assertThat(r.status()).as(r.toString()).isGreaterThanOrEqualTo(500);
        assertThat(r.text()).doesNotContain("Exception", "jdbc:", "PSQLException");

        postgres.getDockerClient().startContainerCmd(postgres.getContainerId()).exec();
        waitForDatabase(db);
        Instant deadline = Instant.now().plusSeconds(30);
        while (!app.store().jsonFiles().isEmpty() && Instant.now().isBefore(deadline)) {
          Mailpit.sleep(500);
        }

        assertThat(app.store().jsonFiles()).as("no accepted JSON file remains").isEmpty();
        assertThat(app.store().registrationsWithEmail(email)).isZero();
        assertThat(app.store().outboxCount()).isZero();
        assertThat(Mailpit.shared().messagesTo(email)).isEmpty();
        Api.Response export = exportWhenReady(app);
        assertThat(new Workbook(export.bytes()).containsText(email)).isFalse();
      }
    } finally {
      postgres.stop();
    }
  }

  private static Api.Response exportWhenReady(AppInstance app) {
    Instant deadline = Instant.now().plusSeconds(60);
    Api.Response export = app.api().export(app.organizerUser(), app.organizerPassword());
    while (export.status() != 200 && Instant.now().isBefore(deadline)) {
      Mailpit.sleep(1000);
      export = app.api().export(app.organizerUser(), app.organizerPassword());
    }
    assertThat(export.status()).as(export.toString()).isEqualTo(200);
    return export;
  }

  private static void waitForDatabase(Infra.DatabaseRef db) {
    Instant deadline = Instant.now().plusSeconds(60);
    while (Instant.now().isBefore(deadline)) {
      try (var c = DriverManager.getConnection(db.url(), db.username(), db.password())) {
        if (c.isValid(2)) {
          return;
        }
      } catch (SQLException e) {
        Mailpit.sleep(500);
      }
    }
    throw new AssertionError("database did not come back");
  }
}

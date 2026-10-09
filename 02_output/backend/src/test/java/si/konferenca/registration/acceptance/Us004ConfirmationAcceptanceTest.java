package si.konferenca.registration.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static si.konferenca.registration.acceptance.support.Api.exportRowsFor;
import static si.konferenca.registration.acceptance.support.Api.external;
import static si.konferenca.registration.acceptance.support.Api.jsonCopies;
import static si.konferenca.registration.acceptance.support.Api.register;
import static si.konferenca.registration.acceptance.support.Api.uniqueEmail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermissions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import si.konferenca.registration.acceptance.support.Api.Response;
import si.konferenca.registration.acceptance.support.Backend;
import tools.jackson.databind.node.ObjectNode;

/** US-004 Registration confirmation: the API outcome the frontend confirmation depends on. */
class Us004ConfirmationAcceptanceTest {

  private static Backend backend;

  @BeforeAll
  static void start() {
    backend = Backend.startDefault();
  }

  @AfterAll
  static void stop() {
    backend.close();
  }

  @Test
  void AC_004_01_acceptedRegistrationReturnsTheConfirmationData() {
    Response response = register(backend, external(uniqueEmail()));

    assertThat(response.status()).as(response.text()).isEqualTo(201);
    assertThat(response.contentType()).startsWith("application/json");
    assertThat(response.json().get("firstName").asString()).isEqualTo("Ana");
    assertThat(response.json().get("lastName").asString()).isEqualTo("Novak");
    assertThat(response.json().get("selectedOptions").size()).isEqualTo(2);
  }

  @Test
  void AC_004_02_rejectedRegistrationReturnsReasonsAndNoConfirmationData() {
    ObjectNode body = external(uniqueEmail());
    body.put("lastName", "");

    Response response = register(backend, body);

    assertThat(response.status()).isEqualTo(400);
    assertThat(response.contentType()).startsWith("application/problem+json");
    assertThat(response.json().has("id")).isFalse();
    assertThat(response.json().get("errors").size()).isPositive();
  }

  @Test
  void AC_004_03_storageFailureReturnsAGeneralErrorWithoutInternalDetails() throws IOException {
    String email = uniqueEmail();
    int copies = jsonCopies(backend).size();
    var original = Files.getPosixFilePermissions(backend.jsonCopyDir());
    Files.setPosixFilePermissions(
        backend.jsonCopyDir(), PosixFilePermissions.fromString("r-x------"));
    Response response;
    try {
      response = register(backend, external(email));
    } finally {
      Files.setPosixFilePermissions(backend.jsonCopyDir(), original);
    }

    assertThat(response.status()).isEqualTo(503);
    assertThat(response.contentType()).startsWith("application/problem+json");
    assertThat(response.json().has("id")).isFalse();
    assertThat(response.text())
        .doesNotContainIgnoringCase("exception")
        .doesNotContain("java.")
        .doesNotContain(backend.jsonCopyDir().toString())
        .doesNotContainIgnoringCase("sql")
        .doesNotContain(email);
    assertThat(exportRowsFor(backend, email)).isEmpty();
    assertThat(jsonCopies(backend)).hasSize(copies);
  }
}
